/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.conversations

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

/**
 * A conversation type offered by the server, with the parameters it applies on creation.
 */
@Parcelize
@JsonObject
data class ConversationPreset(
    @JsonField(name = ["identifier"])
    var identifier: String? = null,
    @JsonField(name = ["name"])
    var name: String? = null,
    @JsonField(name = ["description"])
    var description: String? = null,
    @JsonField(name = ["parameters"])
    var parameters: HashMap<String, Int>? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null)
}
