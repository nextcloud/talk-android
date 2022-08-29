/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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

package com.nextcloud.talk.controllers.bottomsheet

enum class ConversationOperationEnum {
    OPS_CODE_RENAME_ROOM,
    OPS_CODE_GET_AND_JOIN_ROOM,
    OPS_CODE_INVITE_USERS,
    OPS_CODE_MARK_AS_READ,
    OPS_CODE_REMOVE_FAVORITE,
    OPS_CODE_ADD_FAVORITE,
    OPS_CODE_JOIN_ROOM
}
