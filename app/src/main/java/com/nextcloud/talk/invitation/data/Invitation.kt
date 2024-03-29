/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.invitation.data

data class Invitation(
    var id: Int = 0,
    var state: Int = 0,
    var localCloudId: String? = null,
    var localToken: String? = null,
    var remoteAttendeeId: Int = 0,
    var remoteServerUrl: String? = null,
    var remoteToken: String? = null,
    var roomName: String? = null,
    var userId: String? = null,
    var inviterCloudId: String? = null,
    var inviterDisplayName: String? = null
)
