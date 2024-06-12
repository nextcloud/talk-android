/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.participants.Participant

data class ContactEntry(
    val participant: Participant,
    val user: User
) {
}
