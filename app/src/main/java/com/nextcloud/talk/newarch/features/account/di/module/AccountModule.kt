/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.features.account.di.module

import android.app.Application
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.*
import com.nextcloud.talk.newarch.features.account.loginentry.LoginEntryViewModelFactory
import com.nextcloud.talk.newarch.features.account.serverentry.ServerEntryViewModelFactory
import com.nextcloud.talk.utils.preferences.AppPreferences
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val AccountModule = module {
    factory {
        createServerEntryViewModelFactory(
                androidApplication(), get()
        )
    }
    factory {
        createLoginEntryViewModelFactory(androidApplication(), get(), get(), get(), get(), get(), get(), get())
    }
}

fun createServerEntryViewModelFactory(
        application: Application,
        getCapabilitiesUseCase: GetCapabilitiesUseCase
): ServerEntryViewModelFactory {
    return ServerEntryViewModelFactory(
            application, getCapabilitiesUseCase
    )
}

fun createLoginEntryViewModelFactory(
        application: Application,
        getProfileUseCase: GetProfileUseCase,
        getCapabilitiesUseCase: GetCapabilitiesUseCase,
        getSignalingSettingsUseCase: GetSignalingSettingsUseCase,
        registerPushWithServerUseCase: RegisterPushWithServerUseCase,
        registerPushWithProxyUseCase: RegisterPushWithProxyUseCase,
        appPreferences: AppPreferences,
        usersRepository: UsersRepository
): LoginEntryViewModelFactory {
    return LoginEntryViewModelFactory(
            application, getProfileUseCase, getCapabilitiesUseCase, getSignalingSettingsUseCase, registerPushWithServerUseCase, registerPushWithProxyUseCase, appPreferences, usersRepository
    )
}