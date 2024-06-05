/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.dagger.modules

import android.content.Context
import com.nextcloud.talk.chat.data.io.AudioFocusRequestManager
import com.nextcloud.talk.chat.data.io.AudioRecorderManager
import com.nextcloud.talk.chat.data.io.MediaPlayerManager
import com.nextcloud.talk.chat.data.io.MediaRecorderManager
import dagger.Module
import dagger.Provides

@Module
class ManagerModule {

    @Provides
    fun provideMediaRecorderManager(): MediaRecorderManager {
        return MediaRecorderManager()
    }

    @Provides
    fun provideAudioRecorderManager(): AudioRecorderManager {
        return AudioRecorderManager()
    }

    @Provides
    fun provideMediaPlayerManager(): MediaPlayerManager {
        return MediaPlayerManager()
    }

    @Provides
    fun provideAudioFocusManager(context: Context): AudioFocusRequestManager {
        return AudioFocusRequestManager(context)
    }
}
