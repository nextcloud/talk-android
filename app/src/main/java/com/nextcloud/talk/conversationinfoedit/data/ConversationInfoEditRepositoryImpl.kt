/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfoedit.data

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ConversationInfoEditRepositoryImpl(
    private val ncApi: NcApi,
    private val ncApiCoroutines: NcApiCoroutines,
    currentUserProvider: CurrentUserProviderNew
) : ConversationInfoEditRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!

    val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1))

    override fun uploadConversationAvatar(user: User, file: File, roomToken: String): Observable<ConversationModel> {
        val builder = MultipartBody.Builder()
        builder.setType(MultipartBody.FORM)
        builder.addFormDataPart(
            "file",
            file.name,
            file.asRequestBody(Mimetype.IMAGE_PREFIX_GENERIC.toMediaTypeOrNull())
        )
        val filePart: MultipartBody.Part = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody(Mimetype.IMAGE_JPG.toMediaTypeOrNull())
        )

        return ncApi.uploadConversationAvatar(
            credentials,
            ApiUtils.getUrlForConversationAvatar(1, user.baseUrl!!, roomToken),
            filePart
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!, user) }
    }

    override fun deleteConversationAvatar(user: User, roomToken: String): Observable<ConversationModel> =
        ncApi.deleteConversationAvatar(
            credentials,
            ApiUtils.getUrlForConversationAvatar(1, user.baseUrl!!, roomToken)
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!, user) }

    override suspend fun renameConversation(roomToken: String, newRoomName: String): GenericOverall {
        val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))

        return ncApiCoroutines.renameRoom(
            credentials,
            ApiUtils.getUrlForRoom(
                apiVersion,
                currentUser.baseUrl!!,
                roomToken
            ),
            newRoomName
        )
    }

    override suspend fun setConversationDescription(
        roomToken: String,
        conversationDescription: String?
    ): GenericOverall =
        ncApiCoroutines.setConversationDescription(
            credentials,
            ApiUtils.getUrlForConversationDescription(
                apiVersion,
                currentUser.baseUrl!!,
                roomToken
            ),
            conversationDescription
        )
}
