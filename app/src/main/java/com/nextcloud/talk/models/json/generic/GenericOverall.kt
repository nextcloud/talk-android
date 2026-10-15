/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.generic

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class GenericOverall(
    @JsonField(name = ["ocs"])
    var ocs: GenericOCS? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null)
}

private const val HTTP_OK = 200
private const val NO_STATUS_CODE = 0

/**
 * Whether the server accepted the request. An OCS payload can report a refusal in a response the HTTP
 * layer calls a success, so its status code decides. A payload that carries no status code at all is
 * taken at face value, since there is nothing in it to refuse with.
 */
fun GenericOverall.isAccepted(): Boolean =
    ocs?.meta?.statusCode.let { it == null || it == NO_STATUS_CODE || it == HTTP_OK }
