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

package com.nextcloud.talk.chat.data.network

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.chat.data.ChatRepository
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.chat.ChatOverallSingleMessage
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.reminder.Reminder
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.Observable
import retrofit2.Response

class NetworkChatRepositoryImpl(private val ncApi: NcApi) : ChatRepository {
    override fun getRoom(user: User, roomToken: String): Observable<ConversationModel> {
        val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V3, 1))

        return ncApi.getRoom(
            credentials,
            ApiUtils.getUrlForRoom(apiVersion, user.baseUrl!!, roomToken)
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!) }
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
        ).map { ConversationModel.mapToConversationModel(it.ocs?.data!!) }
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
    ): Observable<GenericOverall> {
        return ncApi.sendChatMessage(
            credentials,
            url,
            message,
            displayName,
            null,
            false
        ).map {
            it
        }
    }

    override fun checkForNoteToSelf(
        credentials: String,
        url: String,
        includeStatus: Boolean
    ): Observable<RoomsOverall> {
        return ncApi.getRooms(credentials, url, includeStatus).map { it }
    }

    override fun shareLocationToNotes(
        credentials: String,
        url: String,
        objectType: String,
        objectId: String,
        metadata: String
    ): Observable<GenericOverall> {
        return ncApi.sendLocation(credentials, url, objectType, objectId, metadata).map { it }
    }

    override fun leaveRoom(credentials: String, url: String): Observable<GenericOverall> {
        return ncApi.leaveRoom(credentials, url).map { it }
    }

    override fun sendChatMessage(
        credentials: String,
        url: String,
        message: CharSequence,
        displayName: String,
        replyTo: Int,
        sendWithoutNotification: Boolean
    ): Observable<GenericOverall> {
        return ncApi.sendChatMessage(credentials, url, message, displayName, replyTo, sendWithoutNotification).map {
            it
        }
    }

    override fun pullChatMessages(
        credentials: String,
        url: String,
        fieldMap: HashMap<String, Int>
    ): Observable<Response<*>> {
        return ncApi.pullChatMessages(credentials, url, fieldMap).map { it }
    }

    override fun deleteChatMessage(credentials: String, url: String): Observable<ChatOverallSingleMessage> {
        return ncApi.deleteChatMessage(credentials, url).map { it }
    }

    override fun createRoom(credentials: String, url: String, map: Map<String, String>): Observable<RoomOverall> {
        return ncApi.createRoom(credentials, url, map).map { it }
    }

    override fun setChatReadMarker(
        credentials: String,
        url: String,
        previousMessageId: Int
    ): Observable<GenericOverall> {
        return ncApi.setChatReadMarker(credentials, url, previousMessageId).map { it }
    }

    override fun editChatMessage(credentials: String, url: String, text: String): Observable<ChatOverallSingleMessage> {
        return ncApi.editChatMessage(credentials, url, text).map { it }
    }
}
