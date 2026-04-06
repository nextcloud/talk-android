/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfoedit.data

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.Mimetype
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ConversationInfoEditRepositoryImpl(private val ncApiCoroutines: NcApiCoroutines) :
    ConversationInfoEditRepository {

    override suspend fun getRoom(user: User, roomToken: String): ConversationInfoEditRoomData {
        val credentials = ApiUtils.getCredentials(user.username, user.token) ?: ""
        val apiVersion = ApiUtils.getConversationApiVersion(
            user,
            intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, ApiUtils.API_V1)
        )
        val url = ApiUtils.getUrlForRoom(apiVersion, user.baseUrl, roomToken)
        val result = ncApiCoroutines.getRoom(credentials, url)
        val conversation = ConversationModel.mapToConversationModel(result.ocs?.data!!, user)
        val spreedCapabilities = user.capabilities?.spreedCapability!!
        val descriptionEndpointAvailable =
            CapabilitiesUtil.isConversationDescriptionEndpointAvailable(spreedCapabilities)
        val descriptionMaxLength = CapabilitiesUtil.conversationDescriptionLength(spreedCapabilities)
        return ConversationInfoEditRoomData(
            conversation = conversation,
            descriptionEndpointAvailable = descriptionEndpointAvailable,
            descriptionMaxLength = descriptionMaxLength
        )
    }

    override suspend fun uploadConversationAvatar(user: User, roomToken: String, file: File): ConversationModel {
        val credentials = ApiUtils.getCredentials(user.username, user.token) ?: ""
        val url = ApiUtils.getUrlForConversationAvatar(1, user.baseUrl, roomToken)
        val filePart: MultipartBody.Part = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody(Mimetype.IMAGE_JPG.toMediaTypeOrNull())
        )
        val result = ncApiCoroutines.uploadConversationAvatar(credentials, url, filePart)
        return ConversationModel.mapToConversationModel(result.ocs?.data!!, user)
    }

    override suspend fun deleteConversationAvatar(user: User, roomToken: String): ConversationModel {
        val credentials = ApiUtils.getCredentials(user.username, user.token) ?: ""
        val url = ApiUtils.getUrlForConversationAvatar(1, user.baseUrl, roomToken)
        val result = ncApiCoroutines.deleteConversationAvatar(credentials, url)
        return ConversationModel.mapToConversationModel(result.ocs?.data!!, user)
    }

    override suspend fun renameConversation(user: User, roomToken: String, newRoomName: String): GenericOverall {
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForRoom(apiVersion, user.baseUrl, roomToken)
        return ncApiCoroutines.renameRoom(credentials, url, newRoomName)
    }

    override suspend fun setConversationDescription(
        user: User,
        roomToken: String,
        conversationDescription: String?
    ): GenericOverall {
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val url = ApiUtils.getUrlForConversationDescription(apiVersion, user.baseUrl, roomToken)
        return ncApiCoroutines.setConversationDescription(credentials, url, conversationDescription)
    }
}
