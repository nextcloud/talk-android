/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.tags

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import com.nextcloud.talk.models.json.generic.GenericMeta

@JsonObject
data class ConversationTagErrorOCS(
    @JsonField(name = ["meta"])
    var meta: GenericMeta?,
    @JsonField(name = ["data"])
    var data: ConversationTagErrorResponse? = null
) {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null)
}
