/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.chatprobeattachmentfolder

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.generic.GenericMetaDto
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ChatProbeAttachmentFolderOCS(
    @JsonField(name = ["meta"])
    var meta: GenericMetaDto?,
    @JsonField(name = ["data"])
    var data: ChatProbeAttachmentDataDto? = null
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null)
}
