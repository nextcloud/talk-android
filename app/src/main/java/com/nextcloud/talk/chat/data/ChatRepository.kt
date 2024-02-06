/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.chat.data

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.reminder.Reminder
import io.reactivex.Observable
import retrofit2.Response

@Suppress("LongParameterList", "TooManyFunctions")
interface ChatRepository {
    fun getRoom(user: User, roomToken: String): Observable<ConversationModel>
    fun joinRoom(user: User, roomToken: String, roomPassword: String): Observable<ConversationModel>
    fun setReminder(user: User, roomToken: String, messageId: String, timeStamp: Int): Observable<Reminder>
    fun getReminder(user: User, roomToken: String, messageId: String): Observable<Reminder>
    fun deleteReminder(user: User, roomToken: String, messageId: String): Observable<GenericOverall>
    fun shareToNotes(
        credentials: String,
        url: String,
        message: String,
        displayName: String
    ): Observable<GenericOverall> // last two fields are false
    fun checkForNoteToSelf(credentials: String, url: String, includeStatus: Boolean): Observable<RoomsOverall>
    fun shareLocationToNotes(
        credentials: String,
        url: String,
        objectType: String,
        objectId: String,
        metadata: String
    ): Observable<GenericOverall>
    fun leaveRoom(credentials: String, url: String): Observable<GenericOverall>
    fun sendChatMessage(
        credentials: String,
        url: String,
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean
    ): Observable<GenericOverall>
    fun pullChatMessages(credentials: String, url: String, fieldMap: HashMap<String, Int>): Observable<Response<*>>
    fun deleteChatMessage(credentials: String, url: String): Observable<ChatOverallSingleMessage>
    fun createRoom(credentials: String, url: String, map: Map<String, String>): Observable<RoomOverall>
    fun setChatReadMarker(credentials: String, url: String, previousMessageId: Int): Observable<GenericOverall>
    fun editChatMessage(credentials: String, url: String, text: String): Observable<ChatOverallSingleMessage>
}
