/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.network

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.opengraph.Reference
import com.nextcloud.talk.models.json.reminder.Reminder
import com.nextcloud.talk.models.json.threads.ThreadOverall
import com.nextcloud.talk.models.json.userAbsence.UserAbsenceOverall
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.message.SendMessageUtils
import io.reactivex.Observable
import retrofit2.Response

class RetrofitChatNetwork(private val ncApi: NcApi, private val ncApiCoroutines: NcApiCoroutines) :
    ChatNetworkDataSource {
    override fun getRoom(user: User, roomToken: String): Observable<ConversationModel> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1))

        return ncApi.getRoom(
            credentials,
            ApiUtils.getUrlForRoom(apiVersion, user.baseUrl!!, roomToken)
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!, user) }
    }

    override fun getCapabilities(user: User, roomToken: String): Observable<SpreedCapability> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1))

        return ncApi.getRoomCapabilities(
            credentials,
            ApiUtils.getUrlForRoomCapabilities(apiVersion, user.baseUrl!!, roomToken)
        ).map { it.ocs?.data }
    }

    override fun joinRoom(user: User, roomToken: String, roomPassword: String): Observable<ConversationModel> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, 1))

        return ncApi.joinRoom(
            credentials,
            ApiUtils.getUrlForParticipantsActive(apiVersion, user.baseUrl!!, roomToken),
            roomPassword
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!, user) }
    }

    override fun setReminder(
        user: User,
        roomToken: String,
        messageId: String,
        timeStamp: Int,
        chatApiVersion: Int
    ): Observable<Reminder> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        return ncApi.setReminder(
            credentials,
            ApiUtils.getUrlForReminder(user, roomToken, messageId, chatApiVersion),
            timeStamp
        ).map {
            it.ocs!!.data
        }
    }

    override fun getReminder(
        user: User,
        roomToken: String,
        messageId: String,
        chatApiVersion: Int
    ): Observable<Reminder> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        return ncApi.getReminder(
            credentials,
            ApiUtils.getUrlForReminder(user, roomToken, messageId, chatApiVersion)
        ).map {
            it.ocs!!.data
        }
    }

    override fun deleteReminder(
        user: User,
        roomToken: String,
        messageId: String,
        chatApiVersion: Int
    ): Observable<GenericOverall> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        return ncApi.deleteReminder(
            credentials,
            ApiUtils.getUrlForReminder(user, roomToken, messageId, chatApiVersion)
        ).map {
            it
        }
    }

    override fun shareToNotes(
        credentials: String,
        url: String,
        message: String,
        displayName: String
    ): Observable<ChatOverallSingleMessage> =
        ncApi.sendChatMessage(
            credentials,
            url,
            message,
            displayName,
            null,
            false,
            SendMessageUtils().generateReferenceId()
        ).map {
            it
        }

    override suspend fun checkForNoteToSelf(credentials: String, url: String): RoomOverall =
        ncApiCoroutines.getNoteToSelfRoom(credentials, url)

    override fun shareLocationToNotes(
        credentials: String,
        url: String,
        objectType: String,
        objectId: String,
        metadata: String
    ): Observable<GenericOverall> = ncApi.sendLocation(credentials, url, objectType, objectId, metadata).map { it }

    override fun leaveRoom(credentials: String, url: String): Observable<GenericOverall> =
        ncApi.leaveRoom(credentials, url).map {
            it
        }

    override suspend fun sendChatMessage(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String,
        threadTitle: String?
    ): ChatOverallSingleMessage =
        ncApiCoroutines.sendChatMessage(
            credentials,
            url,
            message,
            displayName,
            replyTo,
            sendWithoutNotification,
            referenceId,
            threadTitle
        )

    override fun pullChatMessages(
        credentials: String,
        url: String,
        fieldMap: HashMap<String, Int>
    ): Observable<Response<*>> = ncApi.pullChatMessages(credentials, url, fieldMap).map { it }

    override fun deleteChatMessage(credentials: String, url: String): Observable<ChatOverallSingleMessage> =
        ncApi.deleteChatMessage(credentials, url).map {
            it
        }

    override fun createRoom(credentials: String, url: String, map: Map<String, String>): Observable<RoomOverall> =
        ncApi.createRoom(credentials, url, map).map {
            it
        }

    override suspend fun createThread(credentials: String, url: String): ThreadOverall =
        ncApiCoroutines.createThread(credentials, url)

    override fun setChatReadMarker(
        credentials: String,
        url: String,
        previousMessageId: Int
    ): Observable<GenericOverall> = ncApi.setChatReadMarker(credentials, url, previousMessageId).map { it }

    override suspend fun editChatMessage(credentials: String, url: String, text: String): ChatOverallSingleMessage =
        ncApiCoroutines.editChatMessage(credentials, url, text)

    override suspend fun getOutOfOfficeStatusForUser(
        credentials: String,
        baseUrl: String,
        userId: String
    ): UserAbsenceOverall =
        ncApiCoroutines.getOutOfOfficeStatusForUser(
            credentials,
            ApiUtils.getUrlForOutOfOffice(baseUrl, userId)
        )

    override suspend fun getContextForChatMessage(
        credentials: String,
        baseUrl: String,
        token: String,
        messageId: String,
        limit: Int
    ): List<ChatMessageJson> {
        val url = ApiUtils.getUrlForChatMessageContext(baseUrl, token, messageId)
        return ncApiCoroutines.getContextOfChatMessage(credentials, url, limit).ocs?.data ?: listOf()
    }

    override suspend fun getOpenGraph(
        credentials: String,
        baseUrl: String,
        extractedLinkToPreview: String
    ): Reference? {
        val openGraphLink = ApiUtils.getUrlForOpenGraph(baseUrl)
        return ncApi.getOpenGraph(
            credentials,
            openGraphLink,
            extractedLinkToPreview
        ).blockingFirst().ocs?.data?.references?.entries?.iterator()?.next()?.value
    }

    override suspend fun unbindRoom(credentials: String, baseUrl: String, roomToken: String): GenericOverall {
        val url = ApiUtils.getUrlForUnbindingRoom(baseUrl, roomToken)
        return ncApiCoroutines.unbindRoom(credentials, url)
    }
}
