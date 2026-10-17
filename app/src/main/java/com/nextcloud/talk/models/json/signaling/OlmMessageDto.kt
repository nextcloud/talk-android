/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.signaling

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

/**
 * An Olm message in the key exchange of end-to-end encrypted calls, used to send it; see NCMessagePayloadDto.key.
 */
@Parcelize
@JsonObject
data class OlmMessageDto(
    @JsonField(name = ["type"])
    var type: Int? = null,
    @JsonField(name = ["body"])
    var body: String? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null)
}
