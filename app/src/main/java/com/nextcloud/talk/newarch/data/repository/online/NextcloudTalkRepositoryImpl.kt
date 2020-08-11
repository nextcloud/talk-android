/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.data.repository.online

import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.chat.ChatOverall
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.notifications.NotificationOverall
import com.nextcloud.talk.models.json.participants.AddParticipantOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.models.json.push.PushRegistrationOverall
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.newarch.data.source.remote.ApiService
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import retrofit2.Response

class NextcloudTalkRepositoryImpl(private val apiService: ApiService) : NextcloudTalkRepository {
    override suspend fun deleteConversationForUser(
            user: UserNgEntity,
            conversation: Conversation
    ): GenericOverall {
        return apiService.deleteConversation(
                user.getCredentials(), ApiUtils.getRoom(user.baseUrl, conversation.token)
        )
    }

    override suspend fun leaveConversationForUser(
            user: UserNgEntity,
            conversation: Conversation
    ): GenericOverall {
        return apiService.leaveConversation(
                user.getCredentials(), ApiUtils.getUrlForRemoveSelfFromRoom(
                user
                        .baseUrl, conversation.token
        )
        )
    }

    override suspend fun getConversationForUser(user: UserNgEntity, conversationToken: String): ConversationOverall {
        return apiService.getConversation(user.getCredentials(), ApiUtils.getRoom(user.baseUrl, conversationToken))
    }

    override suspend fun joinConversationForUser(user: UserNgEntity, conversationToken: String, conversationPassword: String?): ConversationOverall {
        return apiService.joinConversation(user.getCredentials(), ApiUtils.getUrlForSettingMyselfAsActiveParticipant(user.baseUrl, conversationToken), conversationPassword)
    }

    override suspend fun exitConversationForUser(user: User, conversationToken: String): GenericOverall {
        return apiService.exitConversation(user.getCredentials(), ApiUtils.getUrlForSettingMyselfAsActiveParticipant(user.baseUrl, conversationToken))
    }

    override suspend fun getCapabilitiesForServer(server: String): CapabilitiesOverall {
        return apiService.getCapabilities(ApiUtils.getUrlForCapabilities(server))
    }

    override suspend fun setFavoriteValueForConversation(
            user: UserNgEntity,
            conversation: Conversation,
            favorite: Boolean
    ): GenericOverall {
        return if (favorite) {
            apiService.addConversationToFavorites(
                    user.getCredentials(),
                    ApiUtils.getUrlForConversationFavorites(user.baseUrl, conversation.token)
            )
        } else {
            apiService.removeConversationFromFavorites(
                    user.getCredentials(),
                    ApiUtils.getUrlForConversationFavorites(user.baseUrl, conversation.token)
            )
        }
    }

    override suspend fun sendChatMessage(user: User, conversationToken: String, message: CharSequence, authorDisplayName: String?, replyTo: Int?, referenceId: String?): Response<ChatOverall> {
        return apiService.sendChatMessage(user.getCredentials(), ApiUtils.getUrlForChat(user.baseUrl, conversationToken), message, authorDisplayName, replyTo, referenceId)
    }


    override suspend fun getChatMessagesForConversation(user: User, conversationToken: String, lookIntoFuture: Int, lastKnownMessageId: Int, includeLastKnown: Int): Response<ChatOverall> {
        val mutableMap = mutableMapOf<String, Int>()
        mutableMap["lookIntoFuture"] = lookIntoFuture
        mutableMap["lastKnownMessageId"] = lastKnownMessageId
        mutableMap["includeLastKnown"] = includeLastKnown
        mutableMap["timeout"] = 30
        mutableMap["limit"] = 200 //Set max messages received to 200
        mutableMap["setReadMarker"] = 1
        mutableMap["limit"] = 200 //Set max messages received to 200


        return apiService.pullChatMessages(user.getCredentials(), ApiUtils.getUrlForChat(user.baseUrl, conversationToken), mutableMap)
    }

    override suspend fun getNotificationForUser(user: UserNgEntity, notificationId: String): NotificationOverall {
        return apiService.getNotification(user.getCredentials(), ApiUtils.getUrlForNotificationWithId(user.baseUrl, notificationId))
    }

    override suspend fun getParticipantsForCall(user: UserNgEntity, conversationToken: String): ParticipantsOverall {
        return apiService.getPeersForCall(user.getCredentials(), ApiUtils.getUrlForCall(user.baseUrl, conversationToken))
    }

    override suspend fun setPasswordForConversation(user: UserNgEntity, conversationToken: String, password: String): GenericOverall {
        return apiService.setPasswordForConversation(user.getCredentials(), ApiUtils.getUrlForPassword(user.baseUrl, conversationToken), password)
    }

    override suspend fun addParticipantToConversation(user: UserNgEntity, conversationToken: String, participantId: String, source: String): AddParticipantOverall {
        return apiService.addParticipant(user.getCredentials(), ApiUtils.getUrlForParticipants(user.baseUrl, conversationToken), participantId, source)
    }

    override suspend fun createConversationForUser(user: UserNgEntity, conversationType: Int, invite: String?, source: String?, conversationName: String?): ConversationOverall {
        return apiService.createRoom(authorization = user.getCredentials(), url = ApiUtils.getUrlForRoomEndpoint(user.baseUrl), invite = invite, source = source, conversationType = conversationType, conversationName = conversationName)
    }

    override suspend fun getContactsForUser(user: UserNgEntity, groupConversation: Boolean, searchQuery: String?, conversationToken: String?): List<Participant> {
        return apiService.getContacts(authorization = user.getCredentials(), url = ApiUtils.getUrlForContactsSearch(user.baseUrl), shareTypes = ApiUtils.getShareTypesForContactsSearch(user, groupConversation), options = ApiUtils.getQueryMapForContactsSearch(searchQuery, conversationToken)).ocs.data.map {
            val participant = Participant()
            participant.userId = it.id
            participant.displayName = it.label
            participant.source = it.source
            participant
        }
    }

    override suspend fun registerPushWithServerForUser(user: UserNgEntity, options: Map<String, String>): PushRegistrationOverall {
        return apiService.registerForPushWithServer(user.getCredentials(), ApiUtils.getUrlNextcloudPush(user.baseUrl), options)
    }

    override suspend fun unregisterPushWithServerForUser(user: UserNgEntity): GenericOverall {
        return apiService.unregisterForPushWithServer(user.getCredentials(), ApiUtils.getUrlNextcloudPush(user.baseUrl))
    }

    override suspend fun registerPushWithProxyForUser(user: UserNgEntity, options: Map<String, String>): Any {
        return apiService.registerForPushWithProxy(ApiUtils.getUrlPushProxy(), options)
    }

    override suspend fun unregisterPushWithProxyForUser(user: UserNgEntity, options: Map<String, String>): Any {
        return apiService.unregisterForPushWithProxy(ApiUtils.getUrlPushProxy(), options)
    }

    override suspend fun getSignalingSettingsForUser(user: UserNgEntity): SignalingSettingsOverall {
        return apiService.getSignalingSettings(user.getCredentials(), ApiUtils.getUrlForSignalingSettings(user.baseUrl))
    }

    override suspend fun getProfileForUser(user: UserNgEntity): UserProfileOverall {
        return apiService.getUserProfile(user.getCredentials(), ApiUtils.getUrlForUserProfile(user.baseUrl))
    }

    override suspend fun getConversationsForUser(user: UserNgEntity): List<Conversation> {
        return apiService.getConversations(
                user.getCredentials(),
                ApiUtils.getUrlForRoomEndpoint(user.baseUrl)
        )
                .ocs.data
    }
}
