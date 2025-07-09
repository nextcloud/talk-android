/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.dagger.modules

import android.content.Context
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.message.MessageUtils
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtilImpl
import dagger.Module
import dagger.Provides
import dagger.Reusable

@Module(includes = [ContextModule::class])
class UtilsModule {
    @Provides
    @Reusable
    fun providePermissionUtil(context: Context): PlatformPermissionUtil = PlatformPermissionUtilImpl(context)

    @Provides
    @Reusable
    fun provideDateUtils(context: Context): DateUtils = DateUtils(context)

    @Provides
    @Reusable
    fun provideMessageUtils(context: Context): MessageUtils = MessageUtils(context)
}
