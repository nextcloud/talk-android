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

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.nextcloud.talk.newarch.domain.usecases.DeleteConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetConversationsUseCase
import com.nextcloud.talk.newarch.domain.usecases.LeaveConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.SetConversationFavoriteValueUseCase
import com.nextcloud.talk.utils.database.user.UserUtils

class ConversationListViewModelFactory constructor(
  private val application: Application,
  private val conversationsUseCase: GetConversationsUseCase,
  private  val setConversationFavoriteValueUseCase: SetConversationFavoriteValueUseCase,
  private val leaveConversationUseCase: LeaveConversationUseCase,
  private val deleteConversationUseCase: DeleteConversationUseCase,
  private val userUtils: UserUtils
) : ViewModelProvider.Factory {

  override fun <T : ViewModel?> create(modelClass: Class<T>): T {
    return ConversationsListViewModel(application, conversationsUseCase,
        setConversationFavoriteValueUseCase, leaveConversationUseCase, deleteConversationUseCase,
        userUtils) as T
  }
}
