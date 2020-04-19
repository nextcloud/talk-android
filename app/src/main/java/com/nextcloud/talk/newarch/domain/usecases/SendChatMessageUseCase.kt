package com.nextcloud.talk.newarch.domain.usecases

import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.domain.usecases.base.UseCase
import com.nextcloud.talk.newarch.local.models.User
import org.koin.core.parameter.DefinitionParameters
import retrofit2.Response

class SendChatMessageUseCase constructor(
        private val nextcloudTalkRepository: NextcloudTalkRepository,
        apiErrorHandler: ApiErrorHandler?
) : UseCase<Response<ChatOverall>, Any?>(apiErrorHandler) {
    override suspend fun run(params: Any?): Response<ChatOverall> {
        val definitionParameters = params as DefinitionParameters
        val user: User = definitionParameters[0]
        return nextcloudTalkRepository.sendChatMessage(definitionParameters[0], definitionParameters[1], definitionParameters[2], user.displayName, definitionParameters[3], definitionParameters[4])
    }
}
