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
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.ConversationOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.AddParticipantOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.push.PushRegistrationOverall
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import com.nextcloud.talk.newarch.data.source.remote.ApiService
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils

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

    override suspend fun getConversationForUser(userEntity: UserNgEntity, conversationToken: String): ConversationOverall {
        return apiService.getConversation(userEntity.getCredentials(), conversationToken)
    }

    override suspend fun joinConversationForUser(userNgEntity: UserNgEntity, conversationToken: String, conversationPassword: String?): ConversationOverall {
        return apiService.joinConversation(userNgEntity.getCredentials(), ApiUtils.getUrlForSettingMyselfAsActiveParticipant(userNgEntity.baseUrl, conversationToken), conversationPassword)
    }

    override suspend fun exitConversationForUser(userNgEntity: UserNgEntity, conversationToken: String): GenericOverall {
        return apiService.exitConversation(userNgEntity.getCredentials(), ApiUtils.getUrlForSettingMyselfAsActiveParticipant(userNgEntity.baseUrl, conversationToken))
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

    override suspend fun addParticipantToConversation(user: UserNgEntity, conversationToken: String, participantId: String, source: String): AddParticipantOverall {
        return apiService.addParticipant(user.getCredentials(), ApiUtils.getUrlForParticipants(user.baseUrl, conversationToken), participantId, source)
    }

    override suspend fun createConversationForUser(user: UserNgEntity, conversationType: Int, invite: String?, source: String?, conversationName: String?): ConversationOverall {
        return apiService.createRoom(authorization = user.getCredentials(), url = ApiUtils.getUrlForRoomEndpoint(user.baseUrl), invite = invite, source = source, conversationType = conversationType, conversationName = conversationName)
    }

    override suspend fun getContactsForUser(user: UserNgEntity, groupConversation: Boolean, searchQuery: String?, conversationToken: String?): List<Participant> {
        return apiService.getContacts(authorization = user.getCredentials(), url = ApiUtils.getUrlForContactsSearch(user.baseUrl), shareTypes = ApiUtils.getShareTypesForContactsSearch(groupConversation), options = ApiUtils.getQueryMapForContactsSearch(searchQuery, conversationToken)).ocs.data.map {
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
