package com.nextcloud.talk.shareditems.repositories

import android.util.Log
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.json.chat.ChatShareOverall
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedItemType
import com.nextcloud.talk.shareditems.model.SharedMediaItems
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable
import retrofit2.Response
import java.util.Locale
import javax.inject.Inject

class SharedItemsRepositoryImpl @Inject constructor(private val ncApi: NcApi) : SharedItemsRepository {

    override fun media(
        parameters: SharedItemsRepository.Parameters,
        type: SharedItemType
    ): Observable<SharedMediaItems>? {
        return media(parameters, type, null)
    }

    override fun media(
        parameters: SharedItemsRepository.Parameters,
        type: SharedItemType,
        lastKnownMessageId: Int?
    ): Observable<SharedMediaItems>? {
        val credentials = ApiUtils.getCredentials(parameters.userName, parameters.userToken)

        return ncApi.getSharedItems(
            credentials,
            ApiUtils.getUrlForChatSharedItems(1, parameters.baseUrl, parameters.roomToken),
            type.toString().lowercase(Locale.ROOT),
            lastKnownMessageId,
            BATCH_SIZE
        ).map { map(it, parameters) }
    }

    private fun map(
        response: Response<ChatShareOverall>,
        parameters: SharedItemsRepository.Parameters
    ): SharedMediaItems {

        var chatLastGiven: Int? = null
        val items = mutableMapOf<String, SharedItem>()

        if (response.headers()["x-chat-last-given"] != null) {
            chatLastGiven = response.headers()["x-chat-last-given"]!!.toInt()
        }

        val mediaItems = response.body()!!.ocs!!.data
        if (mediaItems != null) {
            for (it in mediaItems) {
                if (it.value.messageParameters?.containsKey("file") == true) {
                    val fileParameters = it.value.messageParameters!!["file"]!!

                    val previewAvailable =
                        "yes".equals(fileParameters["preview-available"]!!, ignoreCase = true)

                    items[it.value.id] = SharedItem(
                        fileParameters["id"]!!,
                        fileParameters["name"]!!,
                        fileParameters["size"]!!.toLong(),
                        it.value.timestamp,
                        fileParameters["path"]!!,
                        fileParameters["link"]!!,
                        fileParameters["mimetype"]!!,
                        previewAvailable,
                        previewLink(fileParameters["id"], parameters.baseUrl),
                        parameters.userEntity
                    )
                } else {
                    Log.w(TAG, "location and deckcard are not yet supported")
                }
            }
        }

        val sortedMutableItems = items.toSortedMap().values.toList().reversed().toMutableList()
        val moreItemsExisting = items.count() == BATCH_SIZE

        return SharedMediaItems(
            sortedMutableItems,
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
