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
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.opengraph.Reference
import com.nextcloud.talk.models.json.reminder.Reminder
import com.nextcloud.talk.models.json.threads.ThreadOverall
import com.nextcloud.talk.models.json.userAbsence.UserAbsenceOverall
import io.reactivex.Observable
import retrofit2.Response

@Suppress("LongParameterList", "TooManyFunctions")
interface ChatNetworkDataSource {
    fun getRoom(user: User, roomToken: String): Observable<ConversationModel>
    fun getCapabilities(user: User, roomToken: String): Observable<SpreedCapability>
    fun joinRoom(user: User, roomToken: String, roomPassword: String): Observable<ConversationModel>
    fun setReminder(
        user: User,
        roomToken: String,
        messageId: String,
        timeStamp: Int,
        chatApiVersion: Int
    ): Observable<Reminder>

    fun getReminder(user: User, roomToken: String, messageId: String, apiVersion: Int): Observable<Reminder>
    fun deleteReminder(user: User, roomToken: String, messageId: String, apiVersion: Int): Observable<GenericOverall>
    fun shareToNotes(
        credentials: String,
        url: String,
        message: String,
        displayName: String
    ): Observable<ChatOverallSingleMessage>

    suspend fun checkForNoteToSelf(credentials: String, url: String): RoomOverall

    fun shareLocationToNotes(
        credentials: String,
        url: String,
        objectType: String,
        objectId: String,
        metadata: String
    ): Observable<GenericOverall>

    fun leaveRoom(credentials: String, url: String): Observable<GenericOverall>
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

    fun pullChatMessages(credentials: String, url: String, fieldMap: HashMap<String, Int>): Observable<Response<*>>
    fun deleteChatMessage(credentials: String, url: String): Observable<ChatOverallSingleMessage>
    fun createRoom(credentials: String, url: String, map: Map<String, String>): Observable<RoomOverall>
    suspend fun createThread(credentials: String, url: String): ThreadOverall
    fun setChatReadMarker(credentials: String, url: String, previousMessageId: Int): Observable<GenericOverall>
    suspend fun editChatMessage(credentials: String, url: String, text: String): ChatOverallSingleMessage
    suspend fun getOutOfOfficeStatusForUser(credentials: String, baseUrl: String, userId: String): UserAbsenceOverall
    suspend fun getContextForChatMessage(
        credentials: String,
        baseUrl: String,
        token: String,
        messageId: String,
        limit: Int
    ): List<ChatMessageJson>
    suspend fun getOpenGraph(credentials: String, baseUrl: String, extractedLinkToPreview: String): Reference?
    suspend fun unbindRoom(credentials: String, baseUrl: String, roomToken: String): GenericOverall
}
