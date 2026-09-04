/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import android.net.Uri
import android.util.Log
import androidx.core.net.toFile
import com.nextcloud.talk.conversationcreation.data.ConversationCreationRepository
import com.nextcloud.talk.conversationinfo.CreateRoomRequest
import com.nextcloud.talk.conversationinfo.Participants
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.SpreedFeatures
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * A conversation that is about to be created.
 */
data class NewConversation(
    val name: String,
    val description: String,
    val preset: String,
    val params: CreateConversationParams,
    val password: String,
    val participants: List<AutocompleteUser>,
    val emoji: String? = null,
    val emojiColor: Int? = null,
    val imageUri: Uri? = null
) {
    val isPasswordProtected: Boolean
        get() = params.roomType == CreateConversationParams.ROOM_TYPE_PUBLIC && password.isNotEmpty()
}

/**
 * Creates conversations on the server, in a single request where the server supports all creation
 * parameters and with follow up requests otherwise.
 */
class ConversationCreator @Inject constructor(private val repository: ConversationCreationRepository) {

    suspend fun create(user: User, newConversation: NewConversation): Conversation? {
        val capabilities = user.capabilities?.spreedCapability
        val credentials = ApiUtils.getCredentials(user.username, user.token)
        val apiVersion = ApiUtils.getConversationApiVersion(user, intArrayOf(ApiUtils.API_V4, ApiUtils.API_V1))
        val context = RequestContext(user, credentials, apiVersion)

        val conversation = if (
            CapabilitiesUtil.hasSpreedFeatureCapability(capabilities, SpreedFeatures.CONVERSATION_CREATION_ALL)
        ) {
            createWithAllParameters(
                context,
                newConversation,
                CapabilitiesUtil.hasSpreedFeatureCapability(
                    capabilities,
                    SpreedFeatures.CONVERSATION_CREATION_PASSWORD
                )
            )
        } else {
            createWithFollowUpRequests(context, newConversation)
        }

        conversation?.token?.let { saveAvatar(context, newConversation, it) }
        return conversation
    }

    private suspend fun createWithAllParameters(
        context: RequestContext,
        newConversation: NewConversation,
        supportsPasswordOnCreation: Boolean
    ): Conversation? {
        val params = newConversation.params
        val sendsPassword = supportsPasswordOnCreation && newConversation.isPasswordProtected
        val body = CreateRoomRequest().apply {
            roomType = params.roomType.toString()
            roomName = newConversation.name
            preset = newConversation.preset.takeIf { it != ConversationPreset.DEFAULT }
            description = newConversation.description
            readOnly = params.readOnly
            listable = params.listable
            messageExpiration = params.messageExpiration
            lobbyState = params.lobbyState
            sipEnabled = params.sipEnabled
            permissions = params.permissions
            recordingConsent = params.recordingConsent
            mentionPermissions = params.mentionPermissions
            participants = participantsOf(newConversation.participants)
            if (sendsPassword) {
                password = newConversation.password
            }
        }

        val url = ApiUtils.getUrlForRooms(context.apiVersion, context.user.baseUrl)
        val conversation = repository.createRoomWithBody(context.credentials, url, body).ocs?.data
        val token = conversation?.token
        if (!token.isNullOrEmpty() && newConversation.isPasswordProtected && !sendsPassword) {
            conversation.hasPassword = applyAfterCreation(context, newConversation, token, passwordOnly = true)
        }
        return conversation
    }

    private suspend fun createWithFollowUpRequests(
        context: RequestContext,
        newConversation: NewConversation
    ): Conversation? {
        val params = newConversation.params
        val retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(
            version = context.apiVersion,
            roomType = params.roomType.toString(),
            baseUrl = context.user.baseUrl,
            conversationName = newConversation.name,
            preset = newConversation.preset.takeIf { it != ConversationPreset.DEFAULT }
        )
        val conversation = repository.createRoom(context.credentials, retrofitBucket).ocs?.data
        val token = conversation?.token?.takeIf { it.isNotEmpty() } ?: return conversation

        if (newConversation.isPasswordProtected) {
            conversation.hasPassword = applyAfterCreation(context, newConversation, token)
        } else {
            applyAfterCreation(context, newConversation, token)
        }
        addParticipants(context, newConversation.participants, token)

        return conversation
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun applyAfterCreation(
        context: RequestContext,
        newConversation: NewConversation,
        token: String,
        passwordOnly: Boolean = false
    ): Boolean {
        var passwordApplied = false
        try {
            if (!passwordOnly && newConversation.description.isNotEmpty()) {
                repository.setConversationDescription(
                    context.credentials,
                    ApiUtils.getUrlForConversationDescription(context.apiVersion, context.user.baseUrl, token),
                    token,
                    newConversation.description
                )
            }

            if (!passwordOnly && newConversation.params.listable != CreateConversationParams.LISTABLE_NONE) {
                repository.openConversation(
                    context.credentials,
                    ApiUtils.getUrlForOpeningConversations(context.apiVersion, context.user.baseUrl, token),
                    token,
                    newConversation.params.listable
                )
            }

            if (newConversation.isPasswordProtected) {
                setPassword(context, newConversation.password, token)
                passwordApplied = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply a setting to the created conversation", e)
        }
        return passwordApplied
    }

    private suspend fun setPassword(context: RequestContext, password: String, token: String) {
        repository.setPassword(
            context.credentials,
            ApiUtils.getUrlForRoomPassword(context.apiVersion, context.user.baseUrl, token),
            token,
            password
        )
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun addParticipants(context: RequestContext, participants: List<AutocompleteUser>, token: String) {
        participants.forEach { participant ->
            val participantId = participant.id ?: return@forEach
            try {
                repository.addParticipants(
                    context.credentials,
                    ApiUtils.getRetrofitBucketForAddParticipantWithSource(
                        context.apiVersion,
                        context.user.baseUrl,
                        token,
                        participant.source ?: SOURCE_USERS,
                        participantId
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add a participant to the created conversation", e)
            }
        }
    }

    private fun participantsOf(autocompleteUsers: List<AutocompleteUser>): Participants {
        val participants = Participants()
        autocompleteUsers.forEach { autocompleteUser ->
            val id = autocompleteUser.id ?: return@forEach
            when (autocompleteUser.source) {
                SOURCE_GROUPS -> participants.groups.add(id)
                SOURCE_EMAILS -> participants.emails.add(id)
                SOURCE_CIRCLES -> participants.teams.add(id)
                SOURCE_FEDERATED -> participants.federatedUsers.add(id)
                SOURCE_PHONES -> participants.phones.add(id)
                else -> participants.users.add(id)
            }
        }
        return participants
    }

    private suspend fun saveAvatar(context: RequestContext, newConversation: NewConversation, token: String) {
        val emoji = newConversation.emoji
        val baseUrl = context.user.baseUrl ?: return
        if (emoji != null) {
            repository.setConversationEmojiAvatar(
                context.credentials,
                ApiUtils.getUrlForConversationEmojiAvatar(1, baseUrl, token),
                emoji,
                newConversation.emojiColor?.let { "%06X".format(COLOR_HEX_MASK and it) }
            )
        } else {
            newConversation.imageUri?.let {
                repository.uploadConversationAvatar(
                    context.credentials,
                    context.user,
                    ApiUtils.getUrlForConversationAvatar(1, baseUrl, token),
                    it.toFile(),
                    token
                )
            }
        }
    }

    private data class RequestContext(val user: User, val credentials: String?, val apiVersion: Int)

    companion object {
        private val TAG = ConversationCreator::class.simpleName
        private const val COLOR_HEX_MASK = 0xFFFFFF
        private const val SOURCE_USERS = "users"
        private const val SOURCE_GROUPS = "groups"
        private const val SOURCE_EMAILS = "emails"
        private const val SOURCE_CIRCLES = "circles"
        private const val SOURCE_FEDERATED = "federated"
        private const val SOURCE_PHONES = "phones"
    }
}
