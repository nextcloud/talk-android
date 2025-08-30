/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.threads

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Thread(
    @JsonField(name = ["id"])
    var id: Int = 0,

    @JsonField(name = ["roomToken"])
    var roomToken: String = "",

    @JsonField(name = ["title"])
    var title: String = "",

    @JsonField(name = ["lastMessageId"])
    var lastMessageId: Int = 0,

    @JsonField(name = ["lastActivity"])
    var lastActivity: Int = 0,

    @JsonField(name = ["numReplies"])
    var numReplies: Int = 0
) : Parcelable
