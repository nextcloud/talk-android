/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models

import android.os.Parcelable
import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare
import kotlinx.parcelize.Parcelize

@Parcelize
data class MessageDraft(
    var messageText: String = "",
    var messageCursor: Int = 0,
    var quotedJsonId: Int? = null,
    var quotedDisplayName: String? = null,
    var quotedMessageText: String? = null,
    var quotedImageUrl: String? = null
): Parcelable

class MessageDraftConverter {

    @TypeConverter
    fun fromMessageDraftToString(messageDraft: MessageDraft?): String {
        return if (messageDraft == null) {
            ""
        } else {
            LoganSquare.serialize(messageDraft)
        }
    }

    @TypeConverter
    fun fromStringToMessageDraft(value: String): MessageDraft? {
        return if (value.isBlank()) {
            null
        } else {
            return LoganSquare.parse(value, MessageDraft::class.java)
        }
    }
}
