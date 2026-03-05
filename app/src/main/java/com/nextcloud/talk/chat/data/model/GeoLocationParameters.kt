/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

class GeoLocationParameters(
    messageParameters: Map<String, Map<String, String>>?
) : RichObjectParameters(messageParameters, "geo-location") {

    val id = string("id")
    val name = string("name")

    val latitude = double("latitude")
    val longitude = double("longitude")
}

