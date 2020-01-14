package com.nextcloud.talk.newarch.features.account.di.module

import android.app.Application
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.domain.usecases.GetCapabilitiesUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetProfileUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetSignalingSettingsUseCase
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
        createLoginEntryViewModelFactory(androidApplication(), get(), get(), get(), get(), get() )
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
        appPreferences: AppPreferences,
        usersRepository: UsersRepository
): LoginEntryViewModelFactory {
    return LoginEntryViewModelFactory(
            application, getProfileUseCase, getCapabilitiesUseCase, getSignalingSettingsUseCase, appPreferences, usersRepository
    )
}