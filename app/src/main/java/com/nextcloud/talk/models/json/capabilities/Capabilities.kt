/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.capabilities

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class Capabilities(
    @JsonField(name = ["core"])
    var coreCapability: CoreCapability?,
    @JsonField(name = ["spreed"])
    var spreedCapability: SpreedCapability?,
    @JsonField(name = ["notifications"])
    var notificationsCapability: NotificationsCapability?,
    @JsonField(name = ["theming"])
    var themingCapability: ThemingCapability?,
    @JsonField(name = ["external"])
    var externalCapability: HashMap<String, List<String>>?,
    @JsonField(name = ["provisioning_api"])
    var provisioningCapability: ProvisioningCapability?,
    @JsonField(name = ["user_status"])
    var userStatusCapability: UserStatusCapability?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, null, null, null, null, null, null)
}
