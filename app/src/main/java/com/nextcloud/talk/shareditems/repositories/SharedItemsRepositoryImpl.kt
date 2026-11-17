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
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.models.json.chat.ChatShareOverall
import com.nextcloud.talk.shareditems.model.SharedDeckCardItem
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedItems
import com.nextcloud.talk.shareditems.model.SharedLocationItem
import com.nextcloud.talk.shareditems.model.SharedOtherItem
import com.nextcloud.talk.shareditems.model.SharedPinnedItem
import com.nextcloud.talk.shareditems.model.SharedPollItem
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateConstants
import com.nextcloud.talk.utils.DateUtils
import retrofit2.HttpException
import retrofit2.Response
import java.util.Locale
import javax.inject.Inject

class SharedItemsRepositoryImpl @Inject constructor(
    private val ncApiCoroutines: NcApiCoroutines,
    private val dateUtils: DateUtils
) : SharedItemsRepository {

    override suspend fun media(
        parameters: SharedItemsRepository.Parameters,
        type: SharedItemType,
        lastKnownMessageId: Int?
    ): SharedItems {
        val credentials = ApiUtils.getCredentials(parameters.userName, parameters.userToken)

        val response = ncApiCoroutines.getSharedItems(
            credentials,
            ApiUtils.getUrlForChatSharedItems(1, parameters.baseUrl, parameters.roomToken),
            type.toString().lowercase(Locale.ROOT),
            lastKnownMessageId,
            BATCH_SIZE
        )
        return map(response, parameters, type)
    }

    @Suppress("LongMethod")
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

        if (!response.isSuccessful) {
            throw HttpException(response)
        }
        val mediaItems = response.body()!!.ocs!!.data
        if (mediaItems != null) {
            for (it in mediaItems) {
                val metaData = it.value.metaData
                val dateTime = dateUtils.getLocalDateTimeStringFromTimestamp(
                    it.value.timestamp * DateConstants.SECOND_DIVIDER
                )

                if (metaData != null) {
                    val fileParameters = it.value.messageParameters?.get("file")
                    val message = it.value.message!!
                    val name = fileParameters?.get("name") ?: message

                    val sharedItem = SharedPinnedItem(
                        it.value.id.toString(),
                        name,
                        it.value.actorId!!,
                        ChatMessage.withServer(
                            metaData.pinnedActorDisplayName!!,
                            metaData.pinnedActorType,
                            metaData.pinnedActorId
                        ),
                        dateTime
                    )
                    items[it.value.id.toString()] = sharedItem
                } else if (it.value.messageParameters?.containsKey("file") == true) {
                    val fileParameters = it.value.messageParameters!!["file"]!!
                    val actorParameters = it.value.messageParameters!!["actor"]!!

                    val previewAvailable =
                        "yes".equals(fileParameters["preview-available"]!!, ignoreCase = true)

                    items[it.value.id.toString()] = SharedFileItem(
                        fileParameters["id"]!!,
                        fileParameters["name"]!!,
                        actorParameters["id"]!!,
                        ChatMessage.withServer(actorParameters["name"]!!, it.value.actorType, it.value.actorId),
                        dateTime,
                        fileParameters["size"]!!.toLong(),
                        fileParameters["path"]!!,
                        fileParameters["link"]!!,
                        fileParameters["mimetype"]!!,
                        previewAvailable,
                        previewLink(fileParameters["id"], parameters.baseUrl!!),
                        it.value.id,
                        it.value.referenceId,
                        it.value.timestamp
                    )
                } else if (it.value.messageParameters?.containsKey("object") == true) {
                    val objectParameters = it.value.messageParameters!!["object"]!!
                    val actorParameters = it.value.messageParameters!!["actor"]!!
                    val actorName = ChatMessage.withServer(
                        actorParameters["name"]!!,
                        it.value.actorType,
                        it.value.actorId
                    )
                    items[it.value.id.toString()] =
                        itemFromObject(objectParameters, actorParameters["id"]!!, actorName, dateTime)
                } else {
                    Log.w(TAG, "Item contains neither 'file' or 'object'.")
                }
            }
        }

        // Sort by numeric message id, not the map key's lexicographic String order (which would
        // sort "10" before "9") - the viewer that consumes this list depends on true chronological
        // order to reconstruct upload-batch groups and page adjacency correctly.
        val sortedMutableItems = items.toSortedMap(compareBy { it.toLong() }).values.toList().reversed().toMutableList()
        // Counts the returned messages, not the mapped items, as messages that cannot be mapped are skipped.
        val moreItemsExisting = mediaItems?.size == BATCH_SIZE

        return SharedItems(
            sortedMutableItems,
            type,
            chatLastGiven,
            moreItemsExisting
        )
    }

    private fun itemFromObject(
        objectParameters: HashMap<String?, String?>,
        actorId: String,
        actorName: String,
        dateTime: String
    ): SharedItem {
        val returnValue: SharedItem
        when (objectParameters["type"]) {
            "talk-poll" -> {
                returnValue = SharedPollItem(
                    objectParameters["id"]!!,
                    objectParameters["name"]!!,
                    actorId,
                    actorName,
                    dateTime
                )
            }

            "geo-location" -> {
                returnValue = SharedLocationItem(
                    objectParameters["id"]!!,
                    objectParameters["name"]!!,
                    actorId,
                    actorName,
                    dateTime,
                    objectParameters["id"]!!.replace("geo:", "geo:0,0?z=11&q=").toUri()
                )
            }

            "deck-card" -> {
                returnValue = SharedDeckCardItem(
                    objectParameters["id"]!!,
                    objectParameters["name"]!!,
                    actorId,
                    actorName,
                    dateTime,
                    objectParameters["link"]!!.toUri()
                )
            }

            else -> {
                returnValue = SharedOtherItem(
                    objectParameters["id"]!!,
                    objectParameters["name"]!!,
                    actorId,
                    actorName,
                    dateTime
                )
            }
        }
        return returnValue
    }

    override suspend fun availableTypes(parameters: SharedItemsRepository.Parameters): Set<SharedItemType> {
        val credentials = ApiUtils.getCredentials(parameters.userName, parameters.userToken)

        val response = ncApiCoroutines.getSharedItemsOverview(
            credentials,
            ApiUtils.getUrlForChatSharedItemsOverview(1, parameters.baseUrl, parameters.roomToken),
            1
        )
        if (response.code() != HTTP_OK) {
            throw HttpException(response)
        }

        return response.body()!!.ocs!!.data!!
            .filterValues { it.isNotEmpty() }
            .keys
            .mapNotNull(::typeOrNull)
            .toSet()
    }

    private fun typeOrNull(name: String): SharedItemType? =
        try {
            SharedItemType.typeFor(name)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Server responds an unknown shared item type: $name")
            null
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
