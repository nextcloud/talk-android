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
import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import com.nextcloud.talk.models.json.chat.ChatMessageDto
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.opengraph.ReferenceDto
import com.nextcloud.talk.models.json.reminder.ReminderDto
import com.nextcloud.talk.models.json.upcomingEvents.UpcomingEventsOverall
import com.nextcloud.talk.models.json.userAbsence.UserAbsenceOverall
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable
import retrofit2.Response

class RetrofitChatNetwork(private val ncApi: NcApi, private val ncApiCoroutines: NcApiCoroutines) :
    ChatNetworkDataSource {
    override suspend fun getRoom(user: User, roomToken: String): ConversationModel {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1))

        val roomOverall = ncApiCoroutines.getRoom(
            credentials,
            ApiUtils.getUrlForRoom(apiVersion, user.baseUrl!!, roomToken)
        )
        return ConversationModel.mapToConversationModel(roomOverall.ocs?.data!!, user)
    }

    override fun getCapabilities(user: User, roomToken: String): Observable<SpreedCapabilityDto> {
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

    override suspend fun setReminder(
        user: User,
        roomToken: String,
        messageId: String,
        timeStamp: Int,
        chatApiVersion: Int
    ): ReminderDto =
        ncApiCoroutines.setReminder(
            ApiUtils.getCredentials(user.username, user.token)!!,
            ApiUtils.getUrlForReminder(user, roomToken, messageId, chatApiVersion),
            timeStamp
        ).ocs!!.data!!

    override suspend fun getReminder(
        user: User,
        roomToken: String,
        messageId: String,
        chatApiVersion: Int
    ): ReminderDto =
        ncApiCoroutines.getReminder(
            ApiUtils.getCredentials(user.username, user.token)!!,
            ApiUtils.getUrlForReminder(user, roomToken, messageId, chatApiVersion)
        ).ocs!!.data!!

    override suspend fun deleteReminder(
        user: User,
        roomToken: String,
        messageId: String,
        chatApiVersion: Int
    ): GenericOverall =
        ncApiCoroutines.deleteReminder(
            ApiUtils.getCredentials(user.username, user.token)!!,
            ApiUtils.getUrlForReminder(user, roomToken, messageId, chatApiVersion)
        )

    override suspend fun shareToNotes(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        referenceId: String
    ): ChatOverallSingleMessage =
        ncApiCoroutines.sendChatMessage(credentials, url, message, displayName, 0, false, referenceId, null)

    override suspend fun checkForNoteToSelf(credentials: String, url: String): RoomOverall =
        ncApiCoroutines.getNoteToSelfRoom(credentials, url)

    override suspend fun shareLocationToNotes(
        credentials: String,
        url: String,
        objectType: String,
        objectId: String,
        metadata: String,
        referenceId: String
    ): GenericOverall = ncApiCoroutines.sendLocation(credentials, url, objectType, objectId, metadata, referenceId)

    override suspend fun leaveRoom(credentials: String, url: String): GenericOverall =
        ncApiCoroutines.leaveRoom(credentials, url)

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

    override suspend fun pullChatMessages(
        credentials: String,
        url: String,
        fieldMap: HashMap<String, Int>
    ): Response<ChatOverall> = ncApiCoroutines.pullChatMessages(credentials, url, fieldMap)

    override suspend fun deleteChatMessage(credentials: String, url: String): ChatOverallSingleMessage =
        ncApiCoroutines.deleteChatMessage(credentials, url)

    override fun createRoom(credentials: String, url: String, map: Map<String, String>): Observable<RoomOverall> =
        ncApi.createRoom(credentials, url, map).map {
            it
        }

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

    override suspend fun getUpcomingEvents(
        credentials: String,
        baseUrl: String,
        roomToken: String
    ): UpcomingEventsOverall =
        ncApiCoroutines.getUpcomingEvents(
            credentials,
            ApiUtils.getUrlForUpcomingEvents(baseUrl, roomToken)
        )

    override suspend fun getContextForChatMessage(
        credentials: String,
        baseUrl: String,
        token: String,
        messageId: String,
        limit: Int,
        threadId: Int?
    ): List<ChatMessageDto> {
        val url = ApiUtils.getUrlForChatMessageContext(baseUrl, token, messageId)
        return ncApiCoroutines.getContextOfChatMessage(credentials, url, limit, threadId).ocs?.data ?: listOf()
    }

    override suspend fun getOpenGraph(
        credentials: String,
        baseUrl: String,
        extractedLinkToPreview: String
    ): ReferenceDto? {
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

    override suspend fun sendScheduledChatMessage(
        credentials: String,
        url: String,
        message: String,
        replyTo: Int?,
        sendWithoutNotification: Boolean,
        threadTitle: String?,
        threadId: Long?,
        sendAt: Int?
    ): ChatOverallSingleMessage =
        ncApiCoroutines.sendScheduleChatMessage(
            credentials,
            url,
            message,
            replyTo,
            sendWithoutNotification,
            threadTitle,
            threadId,
            sendAt
        )

    override suspend fun updateScheduledMessage(
        credentials: String,
        url: String,
        message: String,
        sendAt: Int?,
        sendWithoutNotification: Boolean
    ): ChatOverallSingleMessage =
        ncApiCoroutines.updateScheduledMessage(
            credentials,
            url,
            message,
            sendAt,
            sendWithoutNotification
        )

    override suspend fun deleteScheduledMessage(credentials: String, url: String): GenericOverall =
        ncApiCoroutines.deleteScheduleMessage(credentials, url)

    override suspend fun getScheduledMessages(credentials: String, url: String): ChatOverall =
        ncApiCoroutines.getScheduledMessage(credentials, url)

    override suspend fun pinMessage(credentials: String, url: String, pinUntil: Int): ChatOverallSingleMessage =
        ncApiCoroutines.pinMessage(credentials, url, pinUntil)

    override suspend fun unPinMessage(credentials: String, url: String): ChatOverallSingleMessage =
        ncApiCoroutines.unPinMessage(credentials, url)

    override suspend fun hidePinnedMessage(credentials: String, url: String): GenericOverall =
        ncApiCoroutines.hidePinnedMessage(credentials, url)
}
