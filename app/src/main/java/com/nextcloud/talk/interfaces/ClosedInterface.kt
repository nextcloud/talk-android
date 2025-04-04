/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.interfaces

import android.content.Context

interface ClosedInterface {

    fun isPushMessagingServiceAvailable(context: Context): Boolean
    fun pushMessagingProvider(): String
    fun providerInstallerInstallIfNeededAsync()
    fun registerWithServer(context: Context, username: String?, forceChoose: Boolean): Boolean
    fun unregisterWithServer(context: Context, username: String?)
}
