/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist.ui

internal class ScrollDirectionTracker {
    private var previousIndex: Int? = null
    private var previousOffset = 0
    private var lastDirection: Boolean? = null

    fun onPosition(index: Int, offset: Int): Boolean? {
        val oldIndex = previousIndex
        val oldOffset = previousOffset
        previousIndex = index
        previousOffset = offset
        if (oldIndex == null || (index == oldIndex && offset == oldOffset)) return null
        val direction = index > oldIndex || (index == oldIndex && offset > oldOffset)
        return if (direction != lastDirection) {
            lastDirection = direction
            direction
        } else {
            null
        }
    }
}
