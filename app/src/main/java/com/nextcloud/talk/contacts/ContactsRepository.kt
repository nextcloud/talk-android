/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import com.nextcloud.talk.adapters.items.ContactItem

interface ContactsRepository {
    suspend fun getContacts(
        baseUrl: String,
        ocsApiVersion: String,
        shareList: List<String>,
        options: Map<String, Any>
    ): List<ContactItem>
}
