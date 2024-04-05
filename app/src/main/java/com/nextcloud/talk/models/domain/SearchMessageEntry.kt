/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.domain

data class SearchMessageEntry(
    val searchTerm: String,
    val thumbnailURL: String?,
    val title: String,
    val messageExcerpt: String,
    val conversationToken: String,
    val messageId: String?
)
