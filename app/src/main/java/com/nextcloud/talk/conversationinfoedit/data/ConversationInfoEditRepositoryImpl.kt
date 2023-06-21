/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.conversationinfoedit.data

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

class ConversationInfoEditRepositoryImpl(private val ncApi: NcApi, currentUserProvider: CurrentUserProviderNew) :
    ConversationInfoEditRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)

    val apiVersion = ApiUtils.getConversationApiVersion(currentUser, intArrayOf(ApiUtils.APIv4, ApiUtils.APIv3, 1))

    override fun uploadConversationAvatar(user: User, file: File, roomToken: String): Observable<ConversationModel> {
        val builder = MultipartBody.Builder()
        builder.setType(MultipartBody.FORM)
        builder.addFormDataPart(
            "file",
            file!!.name,
            file.asRequestBody(Mimetype.IMAGE_PREFIX_GENERIC.toMediaTypeOrNull())
        )
        val filePart: MultipartBody.Part = MultipartBody.Part.createFormData(
            "file",
            file.name,
            file.asRequestBody(Mimetype.IMAGE_JPG.toMediaTypeOrNull())
        )

        return ncApi.uploadConversationAvatar(
            credentials,
            ApiUtils.getUrlForConversationAvatar(1, user.baseUrl, roomToken),
            filePart
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!) }
    }

    override fun deleteConversationAvatar(user: User, roomToken: String): Observable<ConversationModel> {
        return ncApi.deleteConversationAvatar(
            credentials,
            ApiUtils.getUrlForConversationAvatar(1, user.baseUrl, roomToken)
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!) }
    }
}
