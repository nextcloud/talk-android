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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.facebook.common.executors.UiThreadImmediateExecutorService
import com.facebook.common.references.CloseableReference
import com.facebook.datasource.DataSource
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber
import com.facebook.imagepipeline.image.CloseableImage
import com.nextcloud.talk.R
import com.nextcloud.talk.R.drawable
import com.nextcloud.talk.R.string
import com.nextcloud.talk.controllers.bottomsheet.items.BasicListItemWithImage
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.usecases.DeleteConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.GetConversationsUseCase
import com.nextcloud.talk.newarch.domain.usecases.LeaveConversationUseCase
import com.nextcloud.talk.newarch.domain.usecases.SetConversationFavoriteValueUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.utils.ViewState
import com.nextcloud.talk.newarch.utils.ViewState.FAILED
import com.nextcloud.talk.newarch.utils.ViewState.LOADED
import com.nextcloud.talk.newarch.utils.ViewState.LOADED_EMPTY
import com.nextcloud.talk.newarch.utils.ViewState.LOADING
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.ShareUtils
import com.nextcloud.talk.utils.database.user.UserUtils
import org.apache.commons.lang3.builder.CompareToBuilder
import org.koin.core.parameter.parametersOf

class ConversationsListViewModel constructor(
  application: Application,
  private val getConversationsUseCase: GetConversationsUseCase,
  private val setConversationFavoriteValueUseCase: SetConversationFavoriteValueUseCase,
  private val leaveConversationUseCase: LeaveConversationUseCase,
  private val deleteConversationUseCase: DeleteConversationUseCase,
  private val userUtils: UserUtils
) : BaseViewModel<ConversationsListView>(application) {

  private var conversations: MutableList<Conversation> = mutableListOf()
  val conversationsLiveListData = MutableLiveData<List<Conversation>>()
  val viewState = MutableLiveData<ViewState>(LOADING)
  var messageData: String? = null
  val searchQuery = MutableLiveData<String>()
  var currentUser: UserEntity = userUtils.currentUser
  var currentUserAvatar: MutableLiveData<Drawable> = MutableLiveData()
    get() {
      if (field.value == null) {
        field.value = context.resources.getDrawable(drawable.ic_settings_white_24dp)
      }

      return field
    }

  fun leaveConversation(conversation: Conversation) {
    leaveConversationUseCase.user = currentUser

    setConversationUpdateStatus(conversation, true)

    leaveConversationUseCase.invoke(viewModelScope, parametersOf(conversation),
        object : UseCaseResponse<GenericOverall> {
          override fun onSuccess(result: GenericOverall) {
            // TODO: Use binary search to find the right room
            conversations.find { it.roomId == conversation.roomId }
                ?.let {
                  conversations.remove(it)
                  conversationsLiveListData.value = conversations
                  if (conversations.isEmpty()) {
                    viewState.value = LOADED_EMPTY
                  }
                }
          }

          override fun onError(errorModel: ErrorModel?) {
            setConversationUpdateStatus(conversation, false)
          }
        })
  }

  fun deleteConversation(conversation: Conversation) {
    deleteConversationUseCase.user = currentUser

    setConversationUpdateStatus(conversation, true)

    deleteConversationUseCase.invoke(viewModelScope, parametersOf(conversation),
        object : UseCaseResponse<GenericOverall> {
          override fun onSuccess(result: GenericOverall) {
            // TODO: Use binary search to find the right room
            conversations.find { it.roomId == conversation.roomId }
                ?.let {
                  conversations.remove(it)
                  conversationsLiveListData.value = conversations
                  if (conversations.isEmpty()) {
                    viewState.value = LOADED_EMPTY
                  }
                }
          }

          override fun onError(errorModel: ErrorModel?) {
            setConversationUpdateStatus(conversation, false)
          }
        })

  }

  fun changeFavoriteValueForConversation(
    conversation: Conversation,
    favorite: Boolean
  ) {
    setConversationFavoriteValueUseCase.user = currentUser

    setConversationUpdateStatus(conversation, true)

    setConversationFavoriteValueUseCase.invoke(viewModelScope, parametersOf(conversation, favorite),
        object : UseCaseResponse<GenericOverall> {
          override fun onSuccess(result: GenericOverall) {
            // TODO: Use binary search to find the right room
            conversations.find { it.roomId == conversation.roomId }
                ?.apply {
                  updating = false
                  isFavorite = favorite
                  conversationsLiveListData.value = conversations
                }
          }

          override fun onError(errorModel: ErrorModel?) {
            setConversationUpdateStatus(conversation, false)
          }
        })
  }

  fun loadConversations() {
    currentUser = userUtils.currentUser

    if ((FAILED).equals(viewState.value) || (LOADED_EMPTY).equals(viewState.value) ||
        !getConversationsUseCase.isUserInitialized() || getConversationsUseCase.user != currentUser
    ) {
      getConversationsUseCase.user = currentUser
      viewState.value = LOADING
    }

    getConversationsUseCase.invoke(
        viewModelScope, null, object : UseCaseResponse<List<Conversation>> {
      override fun onSuccess(result: List<Conversation>) {
        val newConversations = result.toMutableList()

        newConversations.sortWith(Comparator { conversation1, conversation2 ->
          CompareToBuilder()
              .append(conversation2.isFavorite, conversation1.isFavorite)
              .append(conversation2.lastActivity, conversation1.lastActivity)
              .toComparison()
        })

        conversations = newConversations
        conversationsLiveListData.value = conversations
        viewState.value = if (newConversations.isNotEmpty()) LOADED else LOADED_EMPTY
        messageData = ""
      }

      override fun onError(errorModel: ErrorModel?) {
        messageData = errorModel?.getErrorMessage()
        viewState.value = FAILED
      }

    })
  }

  fun loadAvatar(avatarSize: Int) {
    val imageRequest = DisplayUtils.getImageRequestForUrl(
        ApiUtils.getUrlForAvatarWithNameAndPixels(
            currentUser.baseUrl,
            currentUser.userId, avatarSize
        ), null
    )

    val imagePipeline = Fresco.getImagePipeline()
    val dataSource = imagePipeline.fetchDecodedImage(imageRequest, viewModelScope)
    dataSource.subscribe(object : BaseBitmapDataSubscriber() {
      override fun onNewResultImpl(bitmap: Bitmap?) {
        if (bitmap != null) {
          val roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(
              context.resources,
              bitmap
          )
          roundedBitmapDrawable.isCircular = true
          roundedBitmapDrawable.setAntiAlias(true)
          currentUserAvatar.value = roundedBitmapDrawable
        }
      }

      override fun onFailureImpl(dataSource: DataSource<CloseableReference<CloseableImage>>) {
        currentUserAvatar.value = context.getDrawable(R.drawable.ic_settings_white_24dp)
      }
    }, UiThreadImmediateExecutorService.getInstance())

  }

  fun getShareIntentForConversation(conversation: Conversation): Intent {
    val sendIntent: Intent = Intent().apply {
      action = Intent.ACTION_SEND
      putExtra(
          Intent.EXTRA_SUBJECT,
          String.format(
              context.getString(R.string.nc_share_subject),
              context.getString(R.string.nc_app_name)
          )
      )

      // TODO, make sure we ask for password if needed
      putExtra(
          Intent.EXTRA_TEXT, ShareUtils.getStringForIntent(
          context, null,
          userUtils, conversation
      )
      )

      type = "text/plain"
    }

    val intent = Intent.createChooser(sendIntent, context.getString(string.nc_share_link))
    // TODO filter our own app once we're there
    return intent
  }

  fun getConversationMenuItemsForConversation(conversation: Conversation): MutableList<BasicListItemWithImage> {
    val items = mutableListOf<BasicListItemWithImage>()

    if (conversation.isFavorite) {
      items.add(
          BasicListItemWithImage(
              drawable.ic_star_border_black_24dp,
              context.getString(string.nc_remove_from_favorites)
          )
      )
    } else {
      items.add(
          BasicListItemWithImage(
              drawable.ic_star_black_24dp,
              context.getString(string.nc_add_to_favorites)
          )
      )
    }

    if (conversation.isPublic) {
      items.add(
          (BasicListItemWithImage(
              drawable
                  .ic_share_black_24dp, context.getString(string.nc_share_link)
          ))
      )
    }

    if (conversation.canLeave(currentUser)) {
      items.add(
          BasicListItemWithImage(
              drawable.ic_exit_to_app_black_24dp, context.getString
          (string.nc_leave)
          )
      )
    }

    if (conversation.canModerate(currentUser)) {
      items.add(
          BasicListItemWithImage(
              drawable.ic_delete_grey600_24dp, context.getString(
              string.nc_delete_call
          )
          )
      )
    }

    return items
  }

  private fun setConversationUpdateStatus(
    conversation: Conversation,
    value: Boolean
  ) {
    conversations.find { it.roomId == conversation.roomId }
        ?.apply {
          updating = value
          conversationsLiveListData.value = conversations
        }
  }
}
