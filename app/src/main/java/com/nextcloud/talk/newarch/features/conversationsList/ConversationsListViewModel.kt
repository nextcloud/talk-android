/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.newarch.features.conversationsList

import androidx.lifecycle.MutableLiveData
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.usecases.GetConversationsUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.mvvm.ViewState
import com.nextcloud.talk.newarch.mvvm.ViewState.FAILED
import com.nextcloud.talk.newarch.mvvm.ViewState.LOADED
import com.nextcloud.talk.newarch.mvvm.ViewState.LOADING
import com.nextcloud.talk.utils.database.user.UserUtils
import org.apache.commons.lang3.builder.CompareToBuilder

class ConversationsListViewModel constructor(
  private val conversationsUseCase: GetConversationsUseCase,
  private val userUtils: UserUtils
) : BaseViewModel<ConversationsListView>() {

  val conversationsListData = MutableLiveData<List<Conversation>>()
  val viewState = MutableLiveData<ViewState>(LOADING)
  val messageData = MutableLiveData<String>()
  val searchQuery = MutableLiveData<String>()
  lateinit var currentUser: UserEntity

  fun loadConversations() {
    currentUser = userUtils.currentUser

    if (!conversationsUseCase.isUserInitialized() || conversationsUseCase.user != currentUser) {
      conversationsUseCase.user = currentUser
      viewState.value = LOADING
    }

    conversationsUseCase.invoke(
        backgroundAndUIScope, null, object : UseCaseResponse<List<Conversation>> {
      override fun onSuccess(result: List<Conversation>) {
        val newConversations = result.toMutableList()

        newConversations.sortWith(Comparator { conversation1, conversation2 ->
          CompareToBuilder()
              .append(conversation2.isFavorite, conversation1.isFavorite)
              .append(conversation2.lastActivity, conversation1.lastActivity)
              .toComparison()
        })

        conversationsListData.value = newConversations
        viewState.value = LOADED
      }

      override fun onError(errorModel: ErrorModel?) {
        messageData.value = errorModel?.message
        viewState.value = FAILED
      }

    })
  }
}