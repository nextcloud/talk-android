/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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

package com.nextcloud.talk.shareditems.repositories

import android.util.Log
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.chat.ChatShareOverall
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import com.nextcloud.talk.shareditems.model.SharedPollItem
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable
import retrofit2.Response
import java.util.Locale
import javax.inject.Inject

class SharedItemsRepositoryImpl @Inject constructor(private val ncApi: NcApi) : SharedItemsRepository {

    override fun media(
        parameters: SharedItemsRepository.Parameters,
        type: SharedItemType
    ): Observable<SharedItems>? {
        return media(parameters, type, null)
    }

    override fun media(
        parameters: SharedItemsRepository.Parameters,
        type: SharedItemType,
        lastKnownMessageId: Int?
    ): Observable<SharedItems>? {
        val credentials = ApiUtils.getCredentials(parameters.userName, parameters.userToken)

        return ncApi.getSharedItems(
            credentials,
            ApiUtils.getUrlForChatSharedItems(1, parameters.baseUrl, parameters.roomToken),
            type.toString().lowercase(Locale.ROOT),
            lastKnownMessageId,
            BATCH_SIZE
        ).map { map(it, parameters, type) }
    }

    private fun map(
        response: Response<ChatShareOverall>,
        parameters: SharedItemsRepository.Parameters,
        type: SharedItemType
    ): SharedItems {

        var chatLastGiven: Int? = null
        val items = mutableMapOf<String, SharedItem>()

        if (response.headers()["x-chat-last-given"] != null) {
            chatLastGiven = response.headers()["x-chat-last-given"]!!.toInt()
        }

        val mediaItems = response.body()!!.ocs!!.data
        if (mediaItems != null) {
            for (it in mediaItems) {
                val actorParameters = it.value.messageParameters!!["actor"]!!
                if (it.value.messageParameters?.containsKey("file") == true) {
                    val fileParameters = it.value.messageParameters!!["file"]!!

                    val previewAvailable =
                        "yes".equals(fileParameters["preview-available"]!!, ignoreCase = true)

                    items[it.value.id] = SharedFileItem(
                        fileParameters["id"]!!,
                        fileParameters["name"]!!,
                        actorParameters["id"]!!,
                        actorParameters["name"]!!,
                        fileParameters["size"]!!.toLong(),
                        it.value.timestamp,
                        fileParameters["path"]!!,
                        fileParameters["link"]!!,
                        fileParameters["mimetype"]!!,
                        previewAvailable,
                        previewLink(fileParameters["id"], parameters.baseUrl)
                    )
                } else if (it.value.messageParameters?.containsKey("object") == true) {
                    val objectParameters = it.value.messageParameters!!["object"]!!
                    if ("talk-poll" == objectParameters["type"]) {
                        items[it.value.id] = SharedPollItem(
                            objectParameters["id"]!!,
                            objectParameters["name"]!!,
                            actorParameters["id"]!!,
                            actorParameters["name"]!!
                        )
                    }
                } else {
                    Log.w(TAG, "location and deckcard are not yet supported")
                }
            }
        }

        val sortedMutableItems = items.toSortedMap().values.toList().reversed().toMutableList()
        val moreItemsExisting = items.count() == BATCH_SIZE

        return SharedItems(
            sortedMutableItems,
            type,
            chatLastGiven,
            moreItemsExisting
        )
    }

    override fun availableTypes(parameters: SharedItemsRepository.Parameters): Observable<Set<SharedItemType>> {
        val credentials = ApiUtils.getCredentials(parameters.userName, parameters.userToken)

        return ncApi.getSharedItemsOverview(
            credentials,
            ApiUtils.getUrlForChatSharedItemsOverview(1, parameters.baseUrl, parameters.roomToken),
            1
        ).map {
            val types = mutableSetOf<SharedItemType>()
            val typeMap = it.body()!!.ocs!!.data!!
            for (t in typeMap) {
                if (t.value.isNotEmpty()) {
                    try {
                        types += SharedItemType.typeFor(t.key)
                    } catch (e: IllegalArgumentException) {
                        Log.w(TAG, "Server responds an unknown shared item type: ${t.key}")
                    }
                }
            }

            types.toSet()
        }
    }

    private fun previewLink(fileId: String?, baseUrl: String): String {
        return ApiUtils.getUrlForFilePreviewWithFileId(
            baseUrl,
            fileId,
            sharedApplication!!.resources.getDimensionPixelSize(R.dimen.maximum_file_preview_size)
        )
    }

    companion object {
        const val BATCH_SIZE: Int = 28
        private val TAG = SharedItemsRepositoryImpl::class.simpleName
    }
}
