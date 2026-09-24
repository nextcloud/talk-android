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
import com.nextcloud.talk.models.json.chat.ChatMessageDto
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ThreadInfoDto(
    @JsonField(name = ["thread"])
    var thread: ThreadDto? = null,

    @JsonField(name = ["attendee"])
    var attendee: ThreadAttendeeDto? = null,

    @JsonField(name = ["first"])
    var first: ChatMessageDto? = null,

    @JsonField(name = ["last"])
    var last: ChatMessageDto? = null
) : Parcelable
