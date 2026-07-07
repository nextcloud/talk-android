/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.tags

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ConversationTag(
    @JsonField(name = ["id"])
    var id: String = "",

    @JsonField(name = ["name"])
    var name: String = "",

    @JsonField(name = ["sortOrder"])
    var sortOrder: Int = 0,

    @JsonField(name = ["collapsed"])
    var collapsed: Boolean = false,

    @JsonField(name = ["type"])
    var type: String = TYPE_CUSTOM
) : Parcelable {
    companion object {
        const val TYPE_CUSTOM = "custom"
        const val TYPE_FAVORITES = "favorites"
        const val TYPE_OTHER = "other"
    }
}
