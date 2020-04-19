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

package com.nextcloud.talk.newarch.domain.repository.online

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
import com.nextcloud.talk.newarch.local.models.User
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import retrofit2.Response

interface NextcloudTalkRepository {
    suspend fun sendChatMessage(user: User, conversationToken: String, message: CharSequence, authorDisplayName: String?, replyTo: Int?, referenceId: String?): Response<ChatOverall>
    suspend fun getChatMessagesForConversation(user: User, conversationToken: String, lookIntoFuture: Int, lastKnownMessageId: Int, includeLastKnown: Int = 0): Response<ChatOverall>
    suspend fun getNotificationForUser(user: UserNgEntity, notificationId: String): NotificationOverall
    suspend fun getParticipantsForCall(user: UserNgEntity, conversationToken: String): ParticipantsOverall
    suspend fun setPasswordForConversation(user: UserNgEntity, conversationToken: String, password: String): GenericOverall
    suspend fun addParticipantToConversation(user: UserNgEntity, conversationToken: String, participantId: String, source: String): AddParticipantOverall
    suspend fun createConversationForUser(user: UserNgEntity, conversationType: Int, invite: String?, source: String?, conversationName: String?): ConversationOverall
    suspend fun getContactsForUser(user: UserNgEntity, groupConversation: Boolean, searchQuery: String?, conversationToken: String?): List<Participant>
    suspend fun registerPushWithServerForUser(user: UserNgEntity, options: Map<String, String>): PushRegistrationOverall
    suspend fun unregisterPushWithServerForUser(user: UserNgEntity): GenericOverall
    suspend fun registerPushWithProxyForUser(user: UserNgEntity, options: Map<String, String>): Any
    suspend fun unregisterPushWithProxyForUser(user: UserNgEntity, options: Map<String, String>): Any
    suspend fun getSignalingSettingsForUser(user: UserNgEntity): SignalingSettingsOverall
    suspend fun getProfileForUser(user: UserNgEntity): UserProfileOverall
    suspend fun getConversationsForUser(user: UserNgEntity): List<Conversation>
    suspend fun setFavoriteValueForConversation(
            user: UserNgEntity,
            conversation: Conversation,
            favorite: Boolean
    ): GenericOverall

    suspend fun deleteConversationForUser(
            user: UserNgEntity,
            conversation: Conversation
    ): GenericOverall

    suspend fun leaveConversationForUser(
            userEntity: UserNgEntity,
            conversation: Conversation
    ): GenericOverall

    suspend fun getConversationForUser(
            userEntity: UserNgEntity,
            conversationToken: String
    ): ConversationOverall

    suspend fun joinConversationForUser(
            userNgEntity: UserNgEntity,
            conversationToken: String,
            conversationPassword: String?
    ): ConversationOverall

    suspend fun exitConversationForUser(
            userNgEntity: UserNgEntity,
            conversationToken: String
    ): GenericOverall

    suspend fun getCapabilitiesForServer(
            server: String
    ): CapabilitiesOverall
}
