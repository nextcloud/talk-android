/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.push

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class NotificationUserDto(
    @JsonField(name = ["type"])
    var type: String?,

    @JsonField(name = ["id"])
    var id: String?,

    @JsonField(name = ["name"])
    var name: String?,

    @JsonField(name = ["server"])
    var server: String? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null)

    /** The cloud ID of a federated user, or null for everyone else. */
    val cloudId: String?
        get() = remote?.let { "$id@$it" }

    /** The name, followed by the server of a federated user in brackets, e.g. "Alice (cloud.example.com)". */
    val nameWithServer: String?
        get() = remote?.let { "$name ($it)" } ?: name

    /** The server of a federated user as it appears in cloud IDs, which leave out https:// but keep http://. */
    private val remote: String?
        get() = server?.removePrefix("https://")
}
