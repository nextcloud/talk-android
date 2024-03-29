/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.mention

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonIgnore
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Mention(
    @JsonField(name = ["mentionId"])
    var mentionId: String?,
    @JsonField(name = ["id"])
    var id: String?,
    @JsonField(name = ["label"])
    var label: String?,
    // type of user (guests or users or calls)
    @JsonField(name = ["source"])
    var source: String?,
    @JsonField(name = ["status"])
    var status: String?,
    @JsonField(name = ["statusIcon"])
    var statusIcon: String?,
    @JsonField(name = ["statusMessage"])
    var statusMessage: String?,
    @JsonIgnore
    var roomToken: String?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null, null)
}
