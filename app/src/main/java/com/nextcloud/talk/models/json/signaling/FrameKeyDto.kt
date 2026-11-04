/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.signaling

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject

/**
 * The frame key as it travels inside an Olm message of the key exchange, like the web client sends it: the key
 * material in base64 and the index it is stored at.
 */
@JsonObject
data class FrameKeyDto(
    @JsonField(name = ["key"])
    var key: String? = null,
    @JsonField(name = ["index"])
    var index: Int? = null
) {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null)
}
