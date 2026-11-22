/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

internal fun isNewerMessage(previousId: Int?, newestId: Int?): Boolean =
    previousId != null && newestId != null && newestId > 0 && newestId > previousId

internal fun shouldFollowNewMessage(isNearNewest: Boolean, isScrolling: Boolean): Boolean = isNearNewest && !isScrolling
