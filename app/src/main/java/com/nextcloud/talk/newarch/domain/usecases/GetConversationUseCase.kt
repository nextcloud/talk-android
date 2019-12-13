package com.nextcloud.talk.newarch.domain.usecases

import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.domain.usecases.base.UseCase
import org.koin.core.parameter.DefinitionParameters

class GetConversationUseCase constructor(
        private val nextcloudTalkRepository: NextcloudTalkRepository,
        apiErrorHandler: ApiErrorHandler?
) : UseCase<RoomOverall, Any?>(apiErrorHandler) {
    override suspend fun run(params: Any?): RoomOverall {
        val definitionParameters = params as DefinitionParameters
        return nextcloudTalkRepository.getConversationForUser(definitionParameters[0], definitionParameters[1])
    }
}
