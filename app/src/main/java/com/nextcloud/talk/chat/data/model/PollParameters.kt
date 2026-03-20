/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.model

class PollParameters(
    messageParameters: HashMap<String?, HashMap<String?, String?>>?
) : RichObjectParameters(messageParameters, "object") {

    val id = string("id")
    val name = string("name")
}

