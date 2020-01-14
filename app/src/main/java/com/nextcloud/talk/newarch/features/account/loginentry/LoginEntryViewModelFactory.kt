package com.nextcloud.talk.newarch.features.account.loginentry

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.GetCapabilitiesUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetProfileUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetSignalingSettingsUseCase

class LoginEntryViewModelFactory constructor(private val application: Application, private val getProfileUseCase: GetProfileUseCase, private val getCapabilitiesUseCase: GetCapabilitiesUseCase, private val getSignalingSettingsUseCase: GetSignalingSettingsUseCase, private val usersRepository: UsersRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return LoginEntryViewModel(application, getProfileUseCase, getCapabilitiesUseCase, getSignalingSettingsUseCase, usersRepository) as T
    }
}
