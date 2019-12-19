package com.nextcloud.talk.newarch.features.chat

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.usecases.ExitConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.JoinConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import kotlinx.coroutines.launch
import org.koin.core.parameter.parametersOf

class ChatViewModel constructor(application: Application,
                                private val joinConversationUseCase: JoinConversationUseCase,
                                private val exitConversationUseCase: ExitConversationUseCase,
                                private val conversationsRepository: ConversationsRepository,
                                private val messagesRepository: MessagesRepository) : BaseViewModel<ChatView>(application) {
    lateinit var user: UserNgEntity
    val conversation: MutableLiveData<Conversation?> = MutableLiveData()
    val messagesLiveData = Transformations.switchMap(conversation) {
        it?.let {
            messagesRepository.getMessagesWithUserForConversation(it.conversationId!!)
        }
    }
    var conversationPassword: String? = null


    fun init(user: UserNgEntity, conversationToken: String, conversationPassword: String?) {
        viewModelScope.launch {
            this@ChatViewModel.user = user
            this@ChatViewModel.conversation.value = conversationsRepository.getConversationForUserWithToken(user.id!!, conversationToken)
            this@ChatViewModel.conversationPassword = conversationPassword
        }
    }

    suspend fun joinConversation() {
        joinConversationUseCase.invoke(viewModelScope, parametersOf(
                user,
                conversation.value!!.token,
                conversationPassword
        ),
                object : UseCaseResponse<RoomOverall> {
                    override suspend fun onSuccess(result: RoomOverall) {
                        conversationsRepository.saveConversationsForUser(user.id!!, listOf(result.ocs.data))
                    }

                    override fun onError(errorModel: ErrorModel?) {
                        // what do we do on error
                    }
                })
    }

    suspend fun exitConversation() {
        exitConversationUseCase.invoke(backgroundScope, parametersOf(
                user,
                conversation.value!!.token
        ),
                object : UseCaseResponse<GenericOverall> {
                    override suspend fun onSuccess(result: GenericOverall) {
                    }

                    override fun onError(errorModel: ErrorModel?) {
                        // what do we do on error
                    }
                })
    }

    fun sendMessage(message: CharSequence) {

    }

}