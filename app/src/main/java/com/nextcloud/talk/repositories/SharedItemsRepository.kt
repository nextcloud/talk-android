package com.nextcloud.talk.repositories

import android.util.Log
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.chat.ChatShareOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.viewmodels.SharedItemsViewModel
import io.reactivex.Observable
import retrofit2.Response
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SharedItemsRepository {

    var parameters: Parameters? = null

    @Inject
    lateinit var ncApi: NcApi

    init {
        sharedApplication!!.componentApplication.inject(this)
    }

    fun media(type: SharedItemType): Observable<SharedMediaItems>? {
        return media(type, null)
    }

    fun media(type: SharedItemType, lastKnownMessageId: Int?): Observable<SharedMediaItems>? {
        val credentials = ApiUtils.getCredentials(parameters!!.userName, parameters!!.userToken)

        return ncApi.getSharedItems(
            credentials,
            ApiUtils.getUrlForChatSharedItems(1, parameters!!.baseUrl, parameters!!.roomToken),
            type.toString().lowercase(Locale.ROOT),
            lastKnownMessageId,
            BATCH_SIZE
        ).map { map(it, type) }
    }

    private fun map(response: Response<ChatShareOverall>, type: SharedItemType): SharedMediaItems {

        var chatLastGiven: Int? = null
        val items = mutableMapOf<String, SharedItem>()

        if (response.headers()["x-chat-last-given"] != null) {
            chatLastGiven = response.headers()["x-chat-last-given"]!!.toInt()
        }

        val mediaItems = response.body()!!.ocs!!.data
        if (mediaItems != null) {
            for (it in mediaItems) {
                if (it.value.messageParameters.containsKey("file")) {
                    val fileParameters = it.value.messageParameters["file"]!!

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
                        previewLink(fileParameters["id"]),
                        parameters!!.userEntity
                    )
                } else {
                    Log.w(TAG, "location and deckcard are not yet supported")
                }
            }
        }

        val sortedMutableItems = items.toSortedMap().values.toList().reversed().toMutableList()
        val moreItemsExisting = items.count() == SharedItemsViewModel.BATCH_SIZE

        return SharedMediaItems(
            type,
            sortedMutableItems,
            chatLastGiven,
            moreItemsExisting,
            authHeader()
        )
    }

    fun availableTypes(): Observable<Set<SharedItemType>> {
        val credentials = ApiUtils.getCredentials(parameters!!.userName, parameters!!.userToken)

        return ncApi.getSharedItemsOverview(
            credentials,
            ApiUtils.getUrlForChatSharedItemsOverview(1, parameters!!.baseUrl, parameters!!.roomToken),
            1
        ).map {
            val types = mutableSetOf<SharedItemType>()
            val typeMap = it.body()!!.ocs!!.data
            for (t in typeMap) {
                if (t.value.size > 0) {
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

    fun authHeader(): Map<String, String> {
        return mapOf(Pair("Authorization", ApiUtils.getCredentials(parameters!!.userName, parameters!!.userToken)))
    }

    private fun previewLink(fileId: String?): String {
        return ApiUtils.getUrlForFilePreviewWithFileId(
            parameters!!.baseUrl,
            fileId,
            sharedApplication!!.resources.getDimensionPixelSize(R.dimen.maximum_file_preview_size)
        )
    }

    data class Parameters(
        val userName: String,
        val userToken: String,
        val baseUrl: String,
        val userEntity: UserEntity,
        val roomToken: String
    )

    companion object {
        const val BATCH_SIZE: Int = 28
        private val TAG = SharedItemsRepository::class.simpleName
    }
}
