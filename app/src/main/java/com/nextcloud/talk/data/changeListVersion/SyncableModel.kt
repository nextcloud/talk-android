/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.changeListVersion

/**
 * Models any changes from the network, agnostic to what data is being modeled.
 * Implemented by Models that support offline synchronization.
 */
interface SyncableModel {

    /**
     * Model identifier.
     */
    var id: Long

    /**
     * Model deletion checker.
     */
    var markedForDeletion: Boolean
}
