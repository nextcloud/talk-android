package com.nextcloud.talk.newarch.features.chat

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import kotlinx.coroutines.launch

class ChatViewModel constructor(application: Application, private val conversationsRepository: ConversationsRepository) : BaseViewModel<ChatView>(application) {
    lateinit var user: UserNgEntity
    val conversation: MutableLiveData<Conversation?> = MutableLiveData()
    var conversationPassword: String? = null


    fun init(user: UserNgEntity, conversationToken: String, conversationPassword: String?) {
        viewModelScope.launch {
            this@ChatViewModel.user = user
            this@ChatViewModel.conversation.value = conversationsRepository.getConversationForUserWithToken(user.id!!, conversationToken)
            this@ChatViewModel.conversationPassword = conversationPassword
        }
    }

    fun sendMessage(message: CharSequence) {

    }

}