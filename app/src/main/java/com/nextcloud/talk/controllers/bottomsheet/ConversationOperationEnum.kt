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
    RENAME_ROOM,
    MAKE_PUBLIC,
    CHANGE_PASSWORD,
    CLEAR_PASSWORD,
    SET_PASSWORD,
    SHARE_LINK,
    MAKE_PRIVATE,
    GET_AND_JOIN_ROOM,
    INVITE_USERS,
    MARK_AS_READ,
    REMOVE_FAVORITE,
    ADD_FAVORITE,
    JOIN_ROOM
}
