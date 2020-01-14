package com.nextcloud.talk.newarch.di.module

import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.domain.usecases.GetCapabilitiesUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetSignalingSettingsUseCase
import org.koin.dsl.module

val ServerModule = module {
    single { createGetCapabilitiesUseCase(get(), get()) }
    single { createGetSignalingSettingsUseCase(get(), get()) }
}

fun createGetCapabilitiesUseCase(nextcloudTalkRepository: NextcloudTalkRepository,
                                 apiErrorHandler: ApiErrorHandler
): GetCapabilitiesUseCase {
    return GetCapabilitiesUseCase(nextcloudTalkRepository, apiErrorHandler)
}

fun createGetSignalingSettingsUseCase(nextcloudTalkRepository: NextcloudTalkRepository,
                                      apiErrorHandler: ApiErrorHandler
): GetSignalingSettingsUseCase {
    return GetSignalingSettingsUseCase(nextcloudTalkRepository, apiErrorHandler)
}