/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo

import com.nextcloud.talk.conversationinfo.model.ParticipantModel
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.ConversationEnums

data class ConversationInfoUiState(
    val isLoading: Boolean = true,
    val spreedCapabilities: SpreedCapability? = null,
    val capabilitiesVersion: Int = 0,
    val profileDataAvailable: Boolean = false,

    val conversation: ConversationModel? = null,
    val displayName: String = "",
    val description: String = "",
    val avatarUrl: String? = null,
    val conversationType: ConversationEnums.ConversationType? = null,
    val serverBaseUrl: String = "",
    val credentials: String = "",
    val conversationToken: String = "",

    val pronouns: String = "",
    val professionCompany: String = "",
    val localTimeLocation: String = "",

    val upcomingEventSummary: String? = null,
    val upcomingEventTime: String? = null,

    val notificationLevel: String = "",
    val callNotificationsEnabled: Boolean = true,
    val showCallNotifications: Boolean = true,
    val importantConversation: Boolean = false,
    val showImportantConversation: Boolean = false,
    val sensitiveConversation: Boolean = false,
    val showSensitiveConversation: Boolean = false,

    val lobbyEnabled: Boolean = false,
    val showWebinarSettings: Boolean = false,
    val lobbyTimerLabel: String = "",
    val showLobbyTimer: Boolean = false,

    val guestsAllowed: Boolean = false,
    val showGuestAccess: Boolean = false,
    val hasPassword: Boolean = false,
    val showPasswordProtection: Boolean = false,
    val showResendInvitations: Boolean = false,

    val showSharedItems: Boolean = true,
    val showThreadsButton: Boolean = false,

    val showRecordingConsent: Boolean = false,
    val recordingConsentForConversation: Boolean = false,
    val showRecordingConsentSwitch: Boolean = false,
    val showRecordingConsentAll: Boolean = false,
    val recordingConsentEnabled: Boolean = true,

    val messageExpirationLabel: String = "",
    val showMessageExpiration: Boolean = false,
    val showShareConversationButton: Boolean = true,

    val isConversationLocked: Boolean = false,
    val showLockConversation: Boolean = false,

    val participants: List<ParticipantModel> = emptyList(),
    val showParticipants: Boolean = false,
    val showAddParticipants: Boolean = false,
    val showStartGroupChat: Boolean = false,
    val showListBans: Boolean = false,

    val showArchiveConversation: Boolean = false,
    val isArchived: Boolean = false,
    val canLeave: Boolean = true,
    val canDelete: Boolean = false,
    val showClearHistory: Boolean = false,

    val showEditButton: Boolean = false
)
