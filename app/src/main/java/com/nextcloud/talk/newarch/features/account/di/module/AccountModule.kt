package com.nextcloud.talk.newarch.features.account.di.module

import android.app.Application
import com.nextcloud.talk.newarch.domain.usecases.GetCapabilitiesUseCase
import com.nextcloud.talk.newarch.features.account.ServerEntryViewModelFactory
import org.koin.android.ext.koin.androidApplication
import org.koin.dsl.module

val AccountModule = module {
    factory {
        createServerEntryViewModelFactory(
                androidApplication(), get()
        )
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