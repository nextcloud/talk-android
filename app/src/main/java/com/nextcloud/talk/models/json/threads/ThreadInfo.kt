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
import com.nextcloud.talk.models.json.chat.ChatMessageJson
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ThreadInfo(
    @JsonField(name = ["thread"])
    var thread: Thread? = null,

    @JsonField(name = ["attendee"])
    var attendee: ThreadAttendee? = null,

    @JsonField(name = ["first"])
    var first: ChatMessageJson? = null,

    @JsonField(name = ["last"])
    var last: ChatMessageJson? = null
) : Parcelable
