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
import com.nextcloud.talk.utils.Mimetype
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ConversationInfoEditRepositoryImpl(private val ncApiCoroutines: NcApiCoroutines) :
    ConversationInfoEditRepository {

    override suspend fun getRoom(credentials: String, url: String, user: User): ConversationModel {
        val result = ncApiCoroutines.getRoom(credentials, url)
        return ConversationModel.mapToConversationModel(result.ocs?.data!!, user)
    }

    override suspend fun uploadConversationAvatar(
        credentials: String?,
        url: String,
        user: User,
        file: File,
        roomToken: String
    ): ConversationModel {
        val filePart: MultipartBody.Part = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody(Mimetype.IMAGE_JPG.toMediaTypeOrNull())
        )
        val result = ncApiCoroutines.uploadConversationAvatar(
            credentials ?: "",
            url,
            filePart
        )
        return ConversationModel.mapToConversationModel(result.ocs?.data!!, user)
    }

    override suspend fun deleteConversationAvatar(
        credentials: String?,
        url: String,
        user: User,
        roomToken: String
    ): ConversationModel {
        val result = ncApiCoroutines.deleteConversationAvatar(credentials ?: "", url)
        return ConversationModel.mapToConversationModel(result.ocs?.data!!, user)
    }

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
