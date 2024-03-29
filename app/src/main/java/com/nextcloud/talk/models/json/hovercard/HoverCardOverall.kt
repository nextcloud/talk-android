/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.hovercard

import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject

@JsonObject
data class HoverCardOverall(
    @JsonField(name = ["ocs"])
    var ocs: HoverCardOCS?
) {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null)
}
