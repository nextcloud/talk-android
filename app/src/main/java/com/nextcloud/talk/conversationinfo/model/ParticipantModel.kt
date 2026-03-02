/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo.model

import com.nextcloud.talk.models.json.participants.Participant

data class ParticipantModel(val participant: Participant, val isOnline: Boolean)
