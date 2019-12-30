package com.nextcloud.talk.newarch.features.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.usecases.ExitConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.JoinConversationUseCase
import com.nextcloud.talk.newarch.services.GlobalService

class ChatViewModelFactory constructor(
        private val application: Application,
        private val joinConversationUseCase: JoinConversationUseCase,
        private val exitConversationUseCase: ExitConversationUseCase,
        private val conversationsRepository: ConversationsRepository,
        private val messagesRepository: MessagesRepository,
        private val globalService: GlobalService
) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ChatViewModel(
                application, joinConversationUseCase, exitConversationUseCase, conversationsRepository, messagesRepository, globalService
        ) as T
    }
}
