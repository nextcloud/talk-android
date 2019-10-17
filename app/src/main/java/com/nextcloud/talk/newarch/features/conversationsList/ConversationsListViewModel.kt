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
import android.content.res.Resources
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
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.usecases.GetConversationsUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.mvvm.ViewState
import com.nextcloud.talk.newarch.mvvm.ViewState.FAILED
import com.nextcloud.talk.newarch.mvvm.ViewState.LOADED
import com.nextcloud.talk.newarch.mvvm.ViewState.LOADED_EMPTY
import com.nextcloud.talk.newarch.mvvm.ViewState.LOADING
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.database.user.UserUtils
import org.apache.commons.lang3.builder.CompareToBuilder

class ConversationsListViewModel constructor(
  application: Application,
  private val conversationsUseCase: GetConversationsUseCase,
  private val userUtils: UserUtils
) : BaseViewModel<ConversationsListView>(application) {

  val conversationsListData = MutableLiveData<List<Conversation>>()
  val viewState = MutableLiveData<ViewState>(LOADING)
  var messageData: String? = null
  val searchQuery = MutableLiveData<String>()
  var currentUser: UserEntity = userUtils.currentUser
  var currentUserAvatar: MutableLiveData<Drawable> = MutableLiveData()
    get() {
      if (field.value == null) {
        field.value = context.resources.getDrawable(R.drawable.ic_settings_white_24dp)
      }

      return field
    }

  fun loadConversations() {
    currentUser = userUtils.currentUser

    if (viewState.value?.equals(FAILED)!! || !conversationsUseCase.isUserInitialized() ||
        conversationsUseCase.user != currentUser
    ) {
      conversationsUseCase.user = currentUser
      viewState.value = LOADING
    }

    conversationsUseCase.invoke(
        viewModelScope, null, object : UseCaseResponse<List<Conversation>> {
      override fun onSuccess(result: List<Conversation>) {
        val newConversations = result.toMutableList()

        newConversations.sortWith(Comparator { conversation1, conversation2 ->
          CompareToBuilder()
              .append(conversation2.isFavorite, conversation1.isFavorite)
              .append(conversation2.lastActivity, conversation1.lastActivity)
              .toComparison()
        })

        conversationsListData.value = newConversations
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
}
