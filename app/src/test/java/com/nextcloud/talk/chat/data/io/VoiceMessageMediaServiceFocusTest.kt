/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplicationComponent
import com.nextcloud.talk.chat.audio.ChatAudioDataSources
import com.nextcloud.talk.chat.audio.ChatAudioKind
import com.nextcloud.talk.chat.audio.ChatAudioStore
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.utils.preferences.AppPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * The voice message of the chat plays in the player of [VoiceMessageMediaService], which the chat reaches through a
 * MediaController. A voice message must take the transient audio focus, so that the music of another app pauses and
 * comes back afterwards. An audio file uses the focus of the player itself.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceMessageMediaServiceFocusTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val shadow = shadowOf(context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)

    @Before
    fun setUp() {
        // the service injects its dependencies from the application component in onCreate()
        val component = mock<NextcloudTalkApplicationComponent> {
            on { inject(any<VoiceMessageMediaService>()) } doAnswer {
                (it.arguments[0] as VoiceMessageMediaService).apply {
                    dataSources = mock<ChatAudioDataSources> {
                        on { cacheDataSourceFactory } doReturn CacheDataSource.Factory()
                    }
                    store = ChatAudioStore(context)
                    waveformLoader = mock()
                    networkMonitor = mock<NetworkMonitor>()
                    appPreferences = mock<AppPreferences> { on { getChatAudioRepeatMode() } doReturn "OFF" }
                }
                Unit
            }
        }
        setSharedApplication(mock<NextcloudTalkApplication> { on { componentApplication } doReturn component })
    }

    @After
    fun tearDown() {
        // an unfinished service keeps its MediaSession, and the next test fails with "Session ID must be unique"
        service?.onDestroy()
        service = null
        setSharedApplication(null)
    }

    private var service: VoiceMessageMediaService? = null

    private fun createService(): VoiceMessageMediaService =
        Robolectric.buildService(VoiceMessageMediaService::class.java).create().get().also { service = it }

    // The setter of sharedApplication is protected; reflection is the only way in from a test.
    private fun setSharedApplication(application: NextcloudTalkApplication?) {
        NextcloudTalkApplication.Companion::class.java
            .getDeclaredMethod("setSharedApplication", NextcloudTalkApplication::class.java)
            .apply { isAccessible = true }
            .invoke(NextcloudTalkApplication.Companion, application)
    }

    private fun servicePlayer(service: VoiceMessageMediaService): Player {
        val field = VoiceMessageMediaService::class.java.getDeclaredField("mediaSession")
        field.isAccessible = true
        return (field.get(service) as MediaSession).player
    }

    private fun mediaItem(kind: ChatAudioKind): MediaItem {
        val file = File.createTempFile("audio", ".m4a").apply { writeBytes(ByteArray(64)) }
        return MediaItem.Builder()
            .setMediaId("talk-audio:1/token/1")
            .setUri(file.toURI().toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(Bundle().apply { putString("com.nextcloud.talk.chat.audio.KIND", kind.name) })
                    .build()
            )
            .build()
    }

    @Test
    fun `voice message played by the chat service holds a transient audio focus`() {
        val player = servicePlayer(createService())

        player.setMediaItem(mediaItem(ChatAudioKind.VOICE_MESSAGE))
        player.prepare()
        player.play()

        assertNotNull("the chat player must request the audio focus", shadow.lastAudioFocusRequest)
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, shadow.lastAudioFocusRequest.audioFocusRequest.focusGain)
        assertNull(shadow.lastAbandonedAudioFocusRequest)

        player.pause()
        assertNotNull("the music of the other app can come back", shadow.lastAbandonedAudioFocusRequest)
    }

    @Test
    fun `audio file played by the chat service uses the audio focus of the player`() {
        val player = servicePlayer(createService())

        player.setMediaItem(mediaItem(ChatAudioKind.AUDIO_FILE))
        player.prepare()
        player.play()

        // the permanent focus of the player is not visible to the shadow; no transient one may be taken
        assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, player.audioAttributes.contentType)
        assertNull(shadow.lastAudioFocusRequest)
    }
}
