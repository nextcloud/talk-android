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

package com.nextcloud.talk.newarch.features.account.loginentry

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.*
import com.nextcloud.talk.utils.preferences.AppPreferences

class LoginEntryViewModelFactory constructor(private val application: Application, private val getProfileUseCase: GetProfileUseCase, private val getCapabilitiesUseCase: GetCapabilitiesUseCase, private val getSignalingSettingsUseCase: GetSignalingSettingsUseCase, private val registerPushWithServerUseCase: RegisterPushWithServerUseCase, private val registerPushWithProxyUseCase: RegisterPushWithProxyUseCase, private val appPreferences: AppPreferences, private val usersRepository: UsersRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return LoginEntryViewModel(application, getProfileUseCase, getCapabilitiesUseCase, getSignalingSettingsUseCase, registerPushWithServerUseCase, registerPushWithProxyUseCase, appPreferences, usersRepository) as T
    }
}
