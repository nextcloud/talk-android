/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.signaling

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.AnyParceler
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.TypeParceler

@Parcelize
@JsonObject
@TypeParceler<Any?, AnyParceler>
data class NCMessagePayloadDto(
    @JsonField(name = ["type"])
    var type: String? = null,
    @JsonField(name = ["sdp"])
    var sdp: String? = null,
    @JsonField(name = ["nick"])
    var nick: String? = null,
    @JsonField(name = ["candidate"])
    var iceCandidate: NCIceCandidateDto? = null,
    @JsonField(name = ["name"])
    var name: String? = null,
    @JsonField(name = ["state"])
    var state: Boolean? = null,
    @JsonField(name = ["timestamp"])
    var timestamp: Long? = null,
    @JsonField(name = ["reaction"])
    var reaction: String? = null,
    // Key exchange of end-to-end encrypted calls, see EncryptionMessage
    @JsonField(name = ["id"])
    var id: String? = null,
    @JsonField(name = ["identity"])
    var identity: String? = null,
    /**
     * String in "encryption.start" messages, Map with "type" and "body" in the other encryption messages.
     * Use only for received messages
     */
    @JsonField(name = ["key"])
    var key: Any? = null,
    /** Use only to send "encryption.start" messages */
    @JsonField(name = ["key"])
    var keyOneTimeKey: String? = null,
    /** Use only to send the other encryption messages */
    @JsonField(name = ["key"])
    var keyOlmMessage: OlmMessageDto? = null,
    @JsonField(name = ["error"])
    var error: String? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null, null, null, null, null, null, null)
}
