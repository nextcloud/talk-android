/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
