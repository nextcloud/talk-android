/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.io

import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.audio.ChatAudioDataSources
import com.nextcloud.talk.chat.audio.ChatAudioStore
import com.nextcloud.talk.application.NextcloudTalkApplicationComponent
import com.nextcloud.talk.data.network.NetworkMonitor
import com.nextcloud.talk.utils.preferences.AppPreferences
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config

/**
 * Media3 keeps a media service in the foreground, with a notification that cannot be swiped away, for
 * [MediaSessionService.DEFAULT_FOREGROUND_SERVICE_TIMEOUT_MS] (10 minutes) after the player is paused or ends. The
 * voice message service must leave the foreground at once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VoiceMessageMediaServiceForegroundTest {
    @Before
    fun setUp() {
        // the service injects its dependencies from the application component in onCreate()
        val component = mock<NextcloudTalkApplicationComponent> {
            on { inject(any<VoiceMessageMediaService>()) } doAnswer {
                (it.arguments[0] as VoiceMessageMediaService).apply {
                    dataSources = mock<ChatAudioDataSources> {
                        on { cacheDataSourceFactory } doReturn CacheDataSource.Factory()
                    }
                    store = ChatAudioStore(ApplicationProvider.getApplicationContext())
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
        setSharedApplication(null)
    }

    // The setter of sharedApplication is protected; reflection is the only way in from a test.
    private fun setSharedApplication(application: NextcloudTalkApplication?) {
        NextcloudTalkApplication.Companion::class.java
            .getDeclaredMethod("setSharedApplication", NextcloudTalkApplication::class.java)
            .apply { isAccessible = true }
            .invoke(NextcloudTalkApplication.Companion, application)
    }

    /** The timeout Media3 applies to the notification manager of [service], read by reflection (no getter). */
    private fun foregroundTimeoutMs(service: VoiceMessageMediaService): Long {
        val getManager = MediaSessionService::class.java.getDeclaredMethod("getMediaNotificationManager")
        getManager.isAccessible = true
        val manager = getManager.invoke(service)
        val field = manager.javaClass.getDeclaredField("userEngagedTimeoutMs")
        field.isAccessible = true
        return field.getLong(manager)
    }

    @Test
    fun `service does not stay in the foreground after the voice message is paused or ends`() {
        val service = Robolectric.buildService(VoiceMessageMediaService::class.java).create().get()

        assertEquals(0L, foregroundTimeoutMs(service))
        service.onDestroy()
    }
}
