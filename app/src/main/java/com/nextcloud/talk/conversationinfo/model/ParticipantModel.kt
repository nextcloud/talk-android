/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.model

import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.utils.ParticipantRole

data class ParticipantModel(
    val participant: ParticipantDto,
    val isOnline: Boolean,
    val role: ParticipantRole = ParticipantRole.NONE,
    val isSelf: Boolean = false
)
