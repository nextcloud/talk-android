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
import com.nextcloud.talk.utils.Mimetype
import io.reactivex.Observable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ConversationInfoEditRepositoryImpl(private val ncApi: NcApi, private val ncApiCoroutines: NcApiCoroutines) :
    ConversationInfoEditRepository {

    override fun uploadConversationAvatar(
        credentials: String?,
        url: String,
        user: User,
        file: File,
        roomToken: String
    ): Observable<ConversationModel> {
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
            url,
            filePart
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!, user) }
    }

    override fun deleteConversationAvatar(
        credentials: String?,
        url: String,
        user: User,
        roomToken: String
    ): Observable<ConversationModel> =
        ncApi.deleteConversationAvatar(
            credentials,
            url
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!, user) }

    override suspend fun renameConversation(
        credentials: String?,
        url: String,
        roomToken: String,
        newRoomName: String
    ): GenericOverall =
        ncApiCoroutines.renameRoom(
            credentials,
            url,
            newRoomName
        )

    override suspend fun setConversationDescription(
        credentials: String?,
        url: String,
        roomToken: String,
        conversationDescription: String?
    ): GenericOverall =
        ncApiCoroutines.setConversationDescription(
            credentials,
            url,
            conversationDescription
        )
}
