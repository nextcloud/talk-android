/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2019 Shain Singh <shainsingh89@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
