/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import com.nextcloud.talk.UnifiedPush.Companion.getNumberOfDistributorsAvailable
import com.nextcloud.talk.UnifiedPush.Companion.registerForPushMessaging
import com.nextcloud.talk.UnifiedPush.Companion.unregisterForPushMessaging
import com.nextcloud.talk.interfaces.ClosedInterface

class ClosedInterfaceImpl : ClosedInterface {
    override fun providerInstallerInstallIfNeededAsync() { /* nothing */
    }

    override fun isPushMessagingServiceAvailable(context: Context): Boolean {
        return (getNumberOfDistributorsAvailable(context) > 0)
    }

    override fun pushMessagingProvider(): String {
        return "unifiedpush"
    }

    override fun registerWithServer(context: Context, username: String?, forceChoose: Boolean): Boolean {
        // unified push available in generic build
        if (username == null) return false
        return registerForPushMessaging(context, username, forceChoose)
    }

    override fun unregisterWithServer(context: Context, username: String?) {
        // unified push available in generic build
        if (username == null) return
        unregisterForPushMessaging(context, username)
    }
}
