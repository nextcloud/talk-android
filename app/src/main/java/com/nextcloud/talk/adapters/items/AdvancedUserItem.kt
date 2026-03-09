/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.items

import android.accounts.Account
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.participants.Participant

class AdvancedUserItem(
    val model: Participant,
    @JvmField val user: User?,
    val account: Account?,
    val actionRequiredCount: Int
) {
    override fun equals(other: Any?): Boolean =
        if (other is AdvancedUserItem) {
            model == other.model
        } else {
            false
        }

    override fun hashCode(): Int = model.hashCode()
}
