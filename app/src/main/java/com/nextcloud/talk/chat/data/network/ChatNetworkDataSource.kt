/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.data.network

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.opengraph.Reference
import com.nextcloud.talk.models.json.reminder.Reminder
import com.nextcloud.talk.models.json.upcomingEvents.UpcomingEventsOverall
import com.nextcloud.talk.models.json.userAbsence.UserAbsenceOverall
import io.reactivex.Observable
import retrofit2.Response

@Suppress("LongParameterList", "TooManyFunctions")
interface ChatNetworkDataSource {
    fun getRoom(user: User, roomToken: String): Observable<ConversationModel>
    fun getCapabilities(user: User, roomToken: String): Observable<SpreedCapability>
    fun joinRoom(user: User, roomToken: String, roomPassword: String): Observable<ConversationModel>
    suspend fun setReminder(
        user: User,
        roomToken: String,
        messageId: String,
        timeStamp: Int,
        chatApiVersion: Int
    ): Reminder

    suspend fun getReminder(user: User, roomToken: String, messageId: String, apiVersion: Int): Reminder
    suspend fun deleteReminder(user: User, roomToken: String, messageId: String, apiVersion: Int): GenericOverall
    suspend fun shareToNotes(
        credentials: String,
        url: String,
        message: String,
        displayName: String
    ): ChatOverallSingleMessage

    suspend fun checkForNoteToSelf(credentials: String, url: String): RoomOverall

    suspend fun shareLocationToNotes(
        credentials: String,
        url: String,
        objectType: String,
        objectId: String,
        metadata: String
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
    ): List<ChatMessageJson>
    suspend fun getOpenGraph(credentials: String, baseUrl: String, extractedLinkToPreview: String): Reference?
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
