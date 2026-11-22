/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.activities

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.util.Rational
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.nextcloud.talk.R
import com.nextcloud.talk.chat.data.io.MessageAudioPolicy

@OptIn(UnstableApi::class)
internal class VideoPlaybackController(private val activity: MediaViewerActivity) : DefaultLifecycleObserver {
    val player: ExoPlayer = ExoPlayer.Builder(activity)
        .setAudioAttributes(AudioAttributes.DEFAULT, true)
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val handler = Handler(Looper.getMainLooper())
    private val playbackGuard = VideoPlaybackGuard(player, ::canPlay)
    private val action = "${activity.packageName}.VIDEO_PIP_TOGGLE"
    private val monitor = object : Runnable {
        override fun run() {
            playbackGuard.enforcePolicy()
            handler.postDelayed(this, POLICY_CHECK_INTERVAL_MS)
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == action && activity.isInPictureInPictureMode) {
                playbackGuard.toggle()
            }
        }
    }

    init {
        activity.lifecycle.addObserver(this)
        ContextCompat.registerReceiver(activity, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        player.addListener(playbackGuard)
        player.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (activity.isInPictureInPictureMode) activity.setPictureInPictureParams(parameters())
            }
        })
    }

    fun canPlay(): Boolean = MessageAudioPolicy.canPlay(activity)

    fun enterPictureInPicture(): Boolean {
        if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) ||
            !canPlay() ||
            player.playbackState != Player.STATE_READY
        ) {
            return false
        }
        return try {
            activity.enterPictureInPictureMode(parameters())
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }
    }

    private fun parameters(): PictureInPictureParams {
        val video = player.videoSize
        val ratio = VideoPlaybackPolicy.pictureInPictureRatio(video.width, video.height, video.pixelWidthHeightRatio)
        val drawable = if (player.playWhenReady) {
            R.drawable.ic_baseline_pause_voice_message_24
        } else {
            R.drawable.ic_baseline_play_arrow_voice_message_24
        }
        val title = activity.getString(R.string.play_pause_audio_file)
        val toggle = PendingIntent.getBroadcast(
            activity,
            0,
            Intent(action).setPackage(activity.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational((ratio * RATIO_SCALE).toInt(), RATIO_SCALE))
            .setActions(listOf(RemoteAction(Icon.createWithResource(activity, drawable), title, title, toggle)))
            .build()
    }

    override fun onStart(owner: LifecycleOwner) {
        handler.post(monitor)
    }

    override fun onStop(owner: LifecycleOwner) {
        player.pause()
        handler.removeCallbacks(monitor)
    }

    override fun onDestroy(owner: LifecycleOwner) {
        handler.removeCallbacksAndMessages(null)
        activity.unregisterReceiver(receiver)
        player.release()
    }

    companion object {
        private const val POLICY_CHECK_INTERVAL_MS = 250L
        private const val RATIO_SCALE = 1000
    }
}
