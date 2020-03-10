/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.features.chat

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.domain.repository.offline.ConversationsRepository
import com.nextcloud.talk.newarch.domain.repository.offline.MessagesRepository
import com.nextcloud.talk.newarch.domain.usecases.ExitConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.JoinConversationUseCase
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.services.GlobalService
import com.nextcloud.talk.newarch.services.GlobalServiceInterface
import kotlinx.coroutines.launch

class ChatViewModel constructor(application: Application,
                                private val joinConversationUseCase: JoinConversationUseCase,
                                private val exitConversationUseCase: ExitConversationUseCase,
                                private val conversationsRepository: ConversationsRepository,
                                private val messagesRepository: MessagesRepository,
                                private val globalService: GlobalService) : BaseViewModel<ChatView>(application), GlobalServiceInterface {
    lateinit var user: UserNgEntity
    val conversation: MutableLiveData<Conversation?> = MutableLiveData()
    var initConversation: Conversation? = null
    val messagesLiveData = Transformations.switchMap(conversation) {
        it?.let {
            messagesRepository.getMessagesWithUserForConversation(it.conversationId!!)
        }
    }
    var conversationPassword: String? = null


    fun init(user: UserNgEntity, conversationToken: String, conversationPassword: String?) {
        viewModelScope.launch {
            this@ChatViewModel.user = user
            this@ChatViewModel.initConversation = conversationsRepository.getConversationForUserWithToken(user.id, conversationToken)
            this@ChatViewModel.conversationPassword = conversationPassword
            globalService.getConversation(conversationToken, this@ChatViewModel)
        }
    }

    fun sendMessage(message: CharSequence) {

    }

    override suspend fun gotConversationInfoForUser(userNgEntity: UserNgEntity, conversation: Conversation?, operationStatus: GlobalServiceInterface.OperationStatus) {
        if (operationStatus == GlobalServiceInterface.OperationStatus.STATUS_OK) {
            if (userNgEntity.id == user.id && conversation!!.token == initConversation?.token) {
                this.conversation.value = conversationsRepository.getConversationForUserWithToken(user.id, conversation.token!!)
                conversation.token?.let { conversationToken ->
                    globalService.joinConversation(conversationToken, conversationPassword, this)
                }
            }
        }
    }

    override suspend fun joinedConversationForUser(userNgEntity: UserNgEntity, conversation: Conversation?, operationStatus: GlobalServiceInterface.OperationStatus) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}