/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.tags

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ConversationTagDto(
    var id: String = "",
    var name: String = "",
    var sortOrder: Int = 0,
    var collapsed: Boolean = false,
    var type: String = TYPE_CUSTOM
) : Parcelable {
    companion object {
        const val TYPE_CUSTOM = "custom"
        const val TYPE_FAVORITES = "favorites"
        const val TYPE_OTHER = "other"
    }
}
