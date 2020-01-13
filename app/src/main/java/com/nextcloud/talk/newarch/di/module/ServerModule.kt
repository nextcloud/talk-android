package com.nextcloud.talk.newarch.di.module

import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.domain.usecases.GetCapabilitiesUseCase
import org.koin.dsl.module

val ServerModule = module {
    single { createGetCapabilitiesUseCase(get(), get()) }
}

fun createGetCapabilitiesUseCase(nextcloudTalkRepository: NextcloudTalkRepository,
                                 apiErrorHandler: ApiErrorHandler
): GetCapabilitiesUseCase {
    return GetCapabilitiesUseCase(nextcloudTalkRepository, apiErrorHandler)
}