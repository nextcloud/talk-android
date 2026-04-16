/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.items

import android.content.Context
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.mention.Mention
import java.util.Objects

class MentionAutocompleteItem(mention: Mention, context: Context, @JvmField val roomToken: String) {
    @JvmField
    var source: String?

    @JvmField
    val mentionId: String?

    @JvmField
    val objectId: String?

    @JvmField
    val displayName: String?

    @JvmField
    val status: String?

    @JvmField
    val statusIcon: String?

    @JvmField
    val statusMessage: String?

    init {
        mentionId = mention.mentionId
        objectId = mention.id

        displayName = if (!mention.label.isNullOrBlank()) {
            mention.label
        } else if ("guests" == mention.source || "emails" == mention.source) {
            context.resources.getString(R.string.nc_guest)
        } else {
            ""
        }

        source = mention.source
        status = mention.status
        statusIcon = mention.statusIcon
        statusMessage = mention.statusMessage
    }

    override fun equals(other: Any?): Boolean =
        if (other is MentionAutocompleteItem) {
            objectId == other.objectId && displayName == other.displayName
        } else {
            false
        }

    override fun hashCode(): Int = Objects.hash(objectId, displayName)

    companion object {
        const val SOURCE_CALLS = "calls"
        const val SOURCE_GUESTS = "guests"
        const val SOURCE_GROUPS = "groups"
        const val SOURCE_EMAILS = "emails"
        const val SOURCE_TEAMS = "teams"
        const val SOURCE_FEDERATION = "federated_users"
    }
}
