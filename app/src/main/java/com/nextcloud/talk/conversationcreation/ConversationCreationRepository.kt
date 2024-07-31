/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationcreation

import com.nextcloud.talk.models.json.generic.GenericOverall

interface ConversationCreationRepository {
    suspend fun renameConversation(roomToken: String, roomNameNew: String?): GenericOverall
    suspend fun setConversationDescription(roomToken: String, description: String?): GenericOverall
}
