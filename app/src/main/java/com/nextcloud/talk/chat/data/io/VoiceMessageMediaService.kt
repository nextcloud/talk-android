/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.LoadEventInfo
import androidx.media3.exoplayer.source.MediaLoadData
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import autodagger.AutoInjector
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MainActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.audio.ChatAudioDataSources
import com.nextcloud.talk.chat.audio.ChatAudioError
import com.nextcloud.talk.chat.audio.ChatAudioKey
import com.nextcloud.talk.chat.audio.ChatAudioKind
import com.nextcloud.talk.chat.audio.ChatAudioStore
import com.nextcloud.talk.chat.audio.VoiceMessageWaveformLoader
import com.nextcloud.talk.chat.audio.chatAudioKind
import com.nextcloud.talk.chat.audio.toChatAudioError
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Plays voice messages and audio files of chats, also in the background, and exposes them to the notification,
 * the lock screen, headsets and Bluetooth devices. It is the single source of truth for chat audio playback; the
 * UI only controls it through a [androidx.media3.session.MediaController].
 */
@OptIn(UnstableApi::class)
@AutoInjector(NextcloudTalkApplication::class)
class VoiceMessageMediaService : MediaSessionService() {

    @Inject
    lateinit var dataSources: ChatAudioDataSources

    @Inject
    lateinit var store: ChatAudioStore

    @Inject
    lateinit var waveformLoader: VoiceMessageWaveformLoader

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private var mediaSession: MediaSession? = null
    private val serviceScope = MainScope()
    private var retryJob: Job? = null
    private var automaticRetries = 0
    private var currentKey: ChatAudioKey? = null

    override fun onCreate() {
        super.onCreate()
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSources.cacheDataSourceFactory))
            .setAudioAttributes(audioAttributesFor(ChatAudioKind.VOICE_MESSAGE), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(PlayerListener(player))
        player.addAnalyticsListener(LoadListener())

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this).build().apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.let { session ->
            rememberPosition(session.player)
            session.player.release()
            session.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    private fun rememberPosition(player: Player) {
        val key = currentKey
        if (key != null && player.playbackState != Player.STATE_ENDED) {
            store.rememberPosition(key, player.currentPosition, player.duration)
        }
    }

    private fun onItemStarted(player: ExoPlayer, mediaItem: MediaItem?) {
        val key = ChatAudioKey.fromMediaId(mediaItem?.mediaId)
        currentKey = key
        automaticRetries = 0
        retryJob?.cancel()
        mediaSession?.setSessionActivity(key?.let { sessionActivityFor(it) })
        if (mediaItem == null) {
            // Nothing is left to play, so the service only keeps running while a screen is bound to it.
            stopSelf()
        }
        if (key != null && mediaItem != null) {
            player.setAudioAttributes(audioAttributesFor(mediaItem.chatAudioKind()), true)
            store.forgetPosition(key)
        }
    }

    private fun loadWaveform(mediaItem: MediaItem) {
        val key = ChatAudioKey.fromMediaId(mediaItem.mediaId)
        val localConfiguration = mediaItem.localConfiguration
        if (key != null && localConfiguration != null && mediaItem.chatAudioKind() == ChatAudioKind.VOICE_MESSAGE) {
            waveformLoader.load(key, localConfiguration.uri, localConfiguration.customCacheKey)
        }
    }

    private fun sessionActivityFor(key: ChatAudioKey): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(BundleKeys.KEY_INTERNAL_USER_ID, key.internalUserId)
            putExtra(BundleKeys.KEY_ROOM_TOKEN, key.roomToken)
            putExtra(BundleKeys.KEY_MESSAGE_ID, key.messageId.toString())
        }
        return PendingIntent.getActivity(
            this,
            SESSION_ACTIVITY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** Retries after a network error once the device is online again, like a stalled download is resumed. */
    private fun scheduleRetry(player: ExoPlayer, error: PlaybackException) {
        if (error.toChatAudioError() == ChatAudioError.NETWORK && automaticRetries < MAX_AUTOMATIC_RETRIES) {
            retryJob?.cancel()
            retryJob = serviceScope.launch {
                networkMonitor.isOnline.first { it }
                delay(RETRY_DELAY_MS)
                if (player.playerError != null) {
                    automaticRetries++
                    player.prepare()
                }
            }
        }
    }

    private inner class PlayerListener(private val player: ExoPlayer) : Player.Listener {

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            onItemStarted(player, mediaItem)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val oldKey = ChatAudioKey.fromMediaId(oldPosition.mediaItem?.mediaId)
            if (oldKey != null && oldPosition.mediaItem?.mediaId != newPosition.mediaItem?.mediaId) {
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    store.forgetPosition(oldKey)
                } else {
                    store.rememberPosition(oldKey, oldPosition.positionMs, C.TIME_UNSET)
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val key = currentKey
            if (key != null && playbackState == Player.STATE_READY) {
                store.rememberDuration(key, player.duration)
            }
            if (playbackState == Player.STATE_ENDED) {
                key?.let { store.forgetPosition(it) }
                player.stop()
                player.clearMediaItems()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                automaticRetries = 0
            } else {
                rememberPosition(player)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            scheduleRetry(player, error)
        }
    }

    /**
     * Starts computing the waveform of a voice message once the player loaded all of it, as the file is locked in
     * the cache while it is loaded and would otherwise be downloaded a second time.
     */
    private inner class LoadListener : AnalyticsListener {
        override fun onLoadCompleted(
            eventTime: AnalyticsListener.EventTime,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData
        ) {
            val timeline = eventTime.timeline
            if (mediaLoadData.dataType == C.DATA_TYPE_MEDIA && eventTime.windowIndex < timeline.windowCount) {
                loadWaveform(timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem)
            }
        }
    }

    /**
     * Only accepts media items of this app. Other apps may control playback, e.g. from the lock screen or a
     * headset, but can never make the service load their URLs with the credentials of an account.
     */
    private inner class SessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> =
            if (controller.packageName == packageName && mediaItems.all { it.localConfiguration != null }) {
                Futures.immediateFuture(mediaItems)
            } else {
                Futures.immediateFailedFuture<MutableList<MediaItem>>(
                    UnsupportedOperationException("Only Talk can add chat audio")
                )
            }
    }

    companion object {
        private const val SESSION_ACTIVITY_REQUEST_CODE = 4711
        private const val MAX_AUTOMATIC_RETRIES = 3
        private const val RETRY_DELAY_MS = 1000L

        private fun audioAttributesFor(kind: ChatAudioKind): AudioAttributes =
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(
                    if (kind == ChatAudioKind.VOICE_MESSAGE) {
                        C.AUDIO_CONTENT_TYPE_SPEECH
                    } else {
                        C.AUDIO_CONTENT_TYPE_MUSIC
                    }
                )
                .build()
    }
}
