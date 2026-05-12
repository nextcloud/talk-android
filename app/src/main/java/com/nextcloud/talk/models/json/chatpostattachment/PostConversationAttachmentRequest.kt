/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.chatpostattachment

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject

@JsonObject
class PostConversationAttachmentRequest {

    @JsonField(name = ["filePath"])
    var filePath: String? = null

    @JsonField(name = ["referenceId"])
    var referenceId: String? = null

    @JsonField(name = ["talkMetaData"])
    var talkMetaData: String? = null

    @JsonField(name = ["fileName"])
    var fileName: String? = null
}
