/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentsheet

/**
 * Ordered multi-selection of media ids with an upper limit. Immutable; every change returns a new instance.
 */
data class MediaSelection(val limit: Int, val ids: List<Long> = emptyList()) {

    val count: Int
        get() = ids.size

    val isEmpty: Boolean
        get() = ids.isEmpty()

    val isFull: Boolean
        get() = ids.size >= limit

    fun contains(id: Long): Boolean = id in ids

    /**
     * 1-based position of [id] in the selection order, or null when it is not selected.
     */
    fun positionOf(id: Long): Int? = ids.indexOf(id).takeIf { it >= 0 }?.plus(1)

    /**
     * Selects [id] or deselects it when already selected. Selecting is ignored once the limit is reached.
     */
    fun toggle(id: Long): MediaSelection =
        when {
            contains(id) -> copy(ids = ids - id)
            isFull -> this
            else -> copy(ids = ids + id)
        }

    /**
     * Drops ids that are no longer part of [available], e.g. after the media list was reloaded.
     */
    fun retainAvailable(available: Collection<Long>): MediaSelection {
        val availableSet = available.toSet()
        return copy(ids = ids.filter { it in availableSet })
    }
}
