/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.repositories

import android.util.Log
import androidx.core.net.toUri
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.chat.ChatShareOverall
import com.nextcloud.talk.shareditems.model.SharedDeckCardItem
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import com.nextcloud.talk.shareditems.model.SharedLocationItem
import com.nextcloud.talk.shareditems.model.SharedOtherItem
import com.nextcloud.talk.shareditems.model.SharedPollItem
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import io.reactivex.Observable
import retrofit2.Response
import java.util.Locale
import javax.inject.Inject

class SharedItemsRepositoryImpl @Inject constructor(private val ncApi: NcApi, private val dateUtils: DateUtils) :
    SharedItemsRepository {

    override fun media(parameters: SharedItemsRepository.Parameters, type: SharedItemType): Observable<SharedItems>? =
        media(parameters, type, null)

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
                val dateTime = dateUtils.getLocalDateTimeStringFromTimestamp(
                    it.value.timestamp * DateConstants.SECOND_DIVIDER
                )

                if (it.value.messageParameters?.containsKey("file") == true) {
                    val fileParameters = it.value.messageParameters!!["file"]!!

                    val previewAvailable =
                        "yes".equals(fileParameters["preview-available"]!!, ignoreCase = true)

                    items[it.value.id.toString()] = SharedFileItem(
                        fileParameters["id"]!!,
                        fileParameters["name"]!!,
                        actorParameters["id"]!!,
                        actorParameters["name"]!!,
                        dateTime,
                        fileParameters["size"]!!.toLong(),
                        fileParameters["path"]!!,
                        fileParameters["link"]!!,
                        fileParameters["mimetype"]!!,
                        previewAvailable,
                        previewLink(fileParameters["id"], parameters.baseUrl!!)
                    )
                } else if (it.value.messageParameters?.containsKey("object") == true) {
                    val objectParameters = it.value.messageParameters!!["object"]!!
                    items[it.value.id.toString()] = itemFromObject(objectParameters, actorParameters, dateTime)
                } else {
                    Log.w(TAG, "Item contains neither 'file' or 'object'.")
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

    private fun itemFromObject(
        objectParameters: HashMap<String?, String?>,
        actorParameters: HashMap<String?, String?>,
        dateTime: String
    ): SharedItem {
        val returnValue: SharedItem
        when (objectParameters["type"]) {
            "talk-poll" -> {
                returnValue = SharedPollItem(
                    objectParameters["id"]!!,
                    objectParameters["name"]!!,
                    actorParameters["id"]!!,
                    actorParameters["name"]!!,
                    dateTime
                )
            }

            "geo-location" -> {
                returnValue = SharedLocationItem(
                    objectParameters["id"]!!,
                    objectParameters["name"]!!,
                    actorParameters["id"]!!,
                    actorParameters["name"]!!,
                    dateTime,
                    objectParameters["id"]!!.replace("geo:", "geo:0,0?z=11&q=").toUri()
                )
            }

            "deck-card" -> {
                returnValue = SharedDeckCardItem(
                    objectParameters["id"]!!,
                    objectParameters["name"]!!,
                    actorParameters["id"]!!,
                    actorParameters["name"]!!,
                    dateTime,
                    objectParameters["link"]!!.toUri()
                )
            }

            else -> {
                returnValue = SharedOtherItem(
                    objectParameters["id"]!!,
                    objectParameters["name"]!!,
                    actorParameters["id"]!!,
                    actorParameters["name"]!!,
                    dateTime
                )
            }
        }
        return returnValue
    }

    override fun availableTypes(parameters: SharedItemsRepository.Parameters): Observable<Set<SharedItemType>> {
        val credentials = ApiUtils.getCredentials(parameters.userName, parameters.userToken)

        return ncApi.getSharedItemsOverview(
            credentials,
            ApiUtils.getUrlForChatSharedItemsOverview(1, parameters.baseUrl, parameters.roomToken),
            1
        ).map {
            val types = mutableSetOf<SharedItemType>()

            if (it.code() == HTTP_OK) {
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
            } else {
                Log.e(TAG, "Failed to getSharedItemsOverview")
            }

            types.toSet()
        }
    }

    private fun previewLink(fileId: String?, baseUrl: String): String =
        ApiUtils.getUrlForFilePreviewWithFileId(
            baseUrl,
            fileId!!,
            sharedApplication!!.resources.getDimensionPixelSize(R.dimen.maximum_file_preview_size)
        )

    companion object {
        const val BATCH_SIZE: Int = 28
        private const val HTTP_OK: Int = 200
        private val TAG = SharedItemsRepositoryImpl::class.simpleName
    }
}
