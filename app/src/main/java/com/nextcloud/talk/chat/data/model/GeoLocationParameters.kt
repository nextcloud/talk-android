/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

class GeoLocationParameters(messageParameters: HashMap<String?, HashMap<String?, String?>>?) :
    RichObjectParameters(messageParameters, "object") {

    val id = string("id")
    val name = string("name")

    val latitude = double("latitude")
    val longitude = double("longitude")
}
