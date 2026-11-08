/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.tags

import kotlinx.serialization.Serializable

@Serializable
data class ReorderConversationTagsRequestDto(val orderedIds: List<String>)
