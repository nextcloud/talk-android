/*
 * Nextcloud Talk application
 *
 * @author Shain Singh
 * @author Andy Scherzinger
 * Copyright (C) 2021 Shain Singh <shainsingh89@gmail.com>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
 *
 * Based on the MessageSwipeController by Shain Singh at:
 * https://github.com/shainsingh89/SwipeToReply/blob/master/app/src/main/java/com/shain/messenger/SwipeControllerActions.kt
 */

package com.nextcloud.talk.ui.recyclerview

/**
 * Actions executed within a swipe gesture.
 */
interface MessageSwipeActions {

    /**
     * Display reply message including the original, quoted message of/at [position].
     */
    fun showReplyUI(position: Int)
}
