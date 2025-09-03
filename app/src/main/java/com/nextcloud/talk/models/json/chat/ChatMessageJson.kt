/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.chat

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.chat.data.model.ChatMessage.SystemMessageType
import com.nextcloud.talk.models.json.converters.EnumSystemMessageTypeConverter
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ChatMessageJson(
    @JsonField(name = ["id"]) var id: Long = 0,
    @JsonField(name = ["token"]) var token: String? = null,
    @JsonField(name = ["threadId"]) var threadId: Long? = null,

    // Be aware that variables with "is" at the beginning will lead to the error:
    // "@JsonField annotation can only be used on private fields if both getter and setter are present."
    // Instead, name it with "has" at the beginning: isThread -> hasThread
    @JsonField(name = ["isThread"]) var hasThread: Boolean = false,
    @JsonField(name = ["actorType"]) var actorType: String? = null,
    @JsonField(name = ["actorId"]) var actorId: String? = null,
    @JsonField(name = ["actorDisplayName"]) var actorDisplayName: String? = null,
    @JsonField(name = ["timestamp"]) var timestamp: Long = 0,
    @JsonField(name = ["message"]) var message: String? = null,

    @JsonField(name = ["messageParameters"])
    var messageParameters: HashMap<String?, HashMap<String?, String?>>? = null,

    @JsonField(name = ["systemMessage"], typeConverter = EnumSystemMessageTypeConverter::class)
    var systemMessageType: SystemMessageType? = null,

    @JsonField(name = ["isReplyable"]) var replyable: Boolean = false,
    @JsonField(name = ["parent"]) var parentMessage: ChatMessageJson? = null,
    @JsonField(name = ["messageType"]) var messageType: String? = null,
    @JsonField(name = ["reactions"]) var reactions: LinkedHashMap<String, Int>? = null,
    @JsonField(name = ["reactionsSelf"]) var reactionsSelf: ArrayList<String>? = null,
    @JsonField(name = ["expirationTimestamp"]) var expirationTimestamp: Int = 0,
    @JsonField(name = ["markdown"]) var renderMarkdown: Boolean? = null,
    @JsonField(name = ["lastEditActorDisplayName"]) var lastEditActorDisplayName: String? = null,
    @JsonField(name = ["lastEditActorId"]) var lastEditActorId: String? = null,
    @JsonField(name = ["lastEditActorType"]) var lastEditActorType: String? = null,
    @JsonField(name = ["lastEditTimestamp"]) var lastEditTimestamp: Long? = 0,
    @JsonField(name = ["deleted"]) var deleted: Boolean = false,
    @JsonField(name = ["referenceId"]) var referenceId: String? = null,
    @JsonField(name = ["silent"]) var silent: Boolean = false,
    @JsonField(name = ["threadTitle"]) var threadTitle: String? = null,
    @JsonField(name = ["threadReplies"]) var threadReplies: Int? = 0
) : Parcelable
