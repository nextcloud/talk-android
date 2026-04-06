/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfoedit.data

import com.nextcloud.talk.models.domain.ConversationModel

data class ConversationInfoEditRoomData(
    val conversation: ConversationModel,
    val descriptionEndpointAvailable: Boolean,
    val descriptionMaxLength: Int
)
