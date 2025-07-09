/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro.brey@nextcloud.com>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.database.user

import com.nextcloud.talk.dagger.modules.DatabaseModule
import com.nextcloud.talk.data.user.UsersRepository
import com.nextcloud.talk.users.UserManager
import dagger.Binds
import dagger.Module
import dagger.Provides

@Module(includes = [DatabaseModule::class])
abstract class UserModule {

    @Binds
    abstract fun bindCurrentUserProviderNew(currentUserProviderImpl: CurrentUserProviderImpl): CurrentUserProviderNew

    companion object {
        @Provides
        fun provideUserManager(userRepository: UsersRepository): UserManager = UserManager(userRepository)
    }
}
