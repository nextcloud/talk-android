/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.ui

sealed class ParticipantOpsAction {
    object PromoteToOwner : ParticipantOpsAction()
    object PromoteToModerator : ParticipantOpsAction()
    object DemoteOwnerToModerator : ParticipantOpsAction()
    object DemoteOwnerToUser : ParticipantOpsAction()
    object DemoteFromModerator : ParticipantOpsAction()
    object RemoveFromConversation : ParticipantOpsAction()
    object Ban : ParticipantOpsAction()
}
