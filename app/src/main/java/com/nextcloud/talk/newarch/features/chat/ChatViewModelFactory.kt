package com.nextcloud.talk.newarch.features.chat

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository

class ChatViewModelFactory constructor(
        private val application: Application,
        private val conversationsRepository: ConversationsRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel?> create(modelClass: Class<T>): T {
        return ChatViewModel(
                application, conversationsRepository
        ) as T
    }
}
