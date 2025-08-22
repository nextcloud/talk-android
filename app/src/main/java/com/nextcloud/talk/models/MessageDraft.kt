/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models

import android.os.Parcelable
import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@JsonObject
@Serializable
data class MessageDraft(
    @JsonField(name = ["messageText"])
    var messageText: String = "",
    @JsonField(name = ["messageCursor"])
    var messageCursor: Int = 0,
    @JsonField(name = ["quotedJsonId"])
    var quotedJsonId: Int? = null,
    @JsonField(name = ["quotedDisplayName"])
    var quotedDisplayName: String? = null,
    @JsonField(name = ["quotedMessageText"])
    var quotedMessageText: String? = null,
    @JsonField(name = ["quoteImageUrl"])
    var quotedImageUrl: String? = null,
    @JsonField(name = ["threadTitle"])
    var threadTitle: String? = null
) : Parcelable {
    constructor() : this("", 0, null, null, null, null, null)
}

class MessageDraftConverter {

    @TypeConverter
    fun fromMessageDraftToString(messageDraft: MessageDraft?): String =
        if (messageDraft == null) {
            ""
        } else {
            LoganSquare.serialize(messageDraft)
        }

    @TypeConverter
    fun fromStringToMessageDraft(value: String): MessageDraft? =
        if (value.isBlank()) {
            null
        } else {
            LoganSquare.parse(value, MessageDraft::class.java)
        }
}
