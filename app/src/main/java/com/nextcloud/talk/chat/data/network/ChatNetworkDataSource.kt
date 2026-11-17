/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.network

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
import io.reactivex.Observable
import retrofit2.Response

@Suppress("LongParameterList", "TooManyFunctions")
interface ChatNetworkDataSource {
    suspend fun getRoom(user: User, roomToken: String): ConversationModel
    fun getCapabilities(user: User, roomToken: String): Observable<SpreedCapabilityDto>
    fun joinRoom(user: User, roomToken: String, roomPassword: String): Observable<ConversationModel>
    suspend fun setReminder(
        user: User,
        roomToken: String,
        messageId: String,
        timeStamp: Int,
        chatApiVersion: Int
    ): ReminderDto

    suspend fun getReminder(user: User, roomToken: String, messageId: String, apiVersion: Int): ReminderDto
    suspend fun deleteReminder(user: User, roomToken: String, messageId: String, apiVersion: Int): GenericOverall

    @Suppress("LongParameterList")
    suspend fun shareToNotes(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        referenceId: String
    ): ChatOverallSingleMessage

    suspend fun checkForNoteToSelf(credentials: String, url: String): RoomOverall

    @Suppress("LongParameterList")
    suspend fun shareLocationToNotes(
        credentials: String,
        url: String,
        objectType: String,
        objectId: String,
        metadata: String,
        referenceId: String
    ): GenericOverall

    suspend fun leaveRoom(credentials: String, url: String): GenericOverall
    suspend fun sendChatMessage(
        credentials: String,
        url: String,
        message: String,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean,
        referenceId: String,
        threadTitle: String?
    ): ChatOverallSingleMessage

    suspend fun pullChatMessages(
        credentials: String,
        url: String,
        fieldMap: HashMap<String, Int>
    ): Response<ChatOverall>

    suspend fun deleteChatMessage(credentials: String, url: String): ChatOverallSingleMessage
    fun createRoom(credentials: String, url: String, map: Map<String, String>): Observable<RoomOverall>
    fun setChatReadMarker(credentials: String, url: String, previousMessageId: Int): Observable<GenericOverall>
    suspend fun editChatMessage(credentials: String, url: String, text: String): ChatOverallSingleMessage
    suspend fun getOutOfOfficeStatusForUser(credentials: String, baseUrl: String, userId: String): UserAbsenceOverall
    suspend fun getUpcomingEvents(credentials: String, baseUrl: String, roomToken: String): UpcomingEventsOverall
    suspend fun getContextForChatMessage(
        credentials: String,
        baseUrl: String,
        token: String,
        messageId: String,
        limit: Int,
        threadId: Int?
    ): List<ChatMessageDto>
    suspend fun getOpenGraph(credentials: String, baseUrl: String, extractedLinkToPreview: String): ReferenceDto?
    suspend fun unbindRoom(credentials: String, baseUrl: String, roomToken: String): GenericOverall

    suspend fun sendScheduledChatMessage(
        credentials: String,
        url: String,
        message: String,
        replyTo: Int?,
        sendWithoutNotification: Boolean,
        threadTitle: String?,
        threadId: Long?,
        sendAt: Int?
    ): ChatOverallSingleMessage

    suspend fun updateScheduledMessage(
        credentials: String,
        url: String,
        message: String,
        sendAt: Int?,
        sendWithoutNotification: Boolean
    ): ChatOverallSingleMessage

    suspend fun deleteScheduledMessage(credentials: String, url: String): GenericOverall

    suspend fun getScheduledMessages(credentials: String, url: String): ChatOverall

    suspend fun pinMessage(credentials: String, url: String, pinUntil: Int): ChatOverallSingleMessage

    suspend fun unPinMessage(credentials: String, url: String): ChatOverallSingleMessage

    suspend fun hidePinnedMessage(credentials: String, url: String): GenericOverall
}
