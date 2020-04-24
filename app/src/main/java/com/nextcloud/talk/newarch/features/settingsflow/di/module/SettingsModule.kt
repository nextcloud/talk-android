package com.nextcloud.talk.newarch.features.settingsflow.di.module

import android.app.Application
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.features.settingsflow.settings.SettingsViewModelFactory
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.utils.NetworkComponents
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val SettingsModule = module {
    factory {
        createSettingsViewModelFactory(
                androidApplication(), get(), get(), get(), get()
        )
    }
}

fun createSettingsViewModelFactory(application: Application, usersRepository: UsersRepository, networkComponents: NetworkComponents, apiErrorHandler: ApiErrorHandler, globalService: GlobalService): SettingsViewModelFactory {
    return SettingsViewModelFactory(application, usersRepository, networkComponents, apiErrorHandler, globalService)
}