/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Sven R. Kunze
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import third.parties.daveKoeller.AlphanumComparator
import java.util.Collections

class FileSortOrderByName internal constructor(name: String, ascending: Boolean) : FileSortOrder(name, ascending) {
    /**
     * Sorts list by Name.
     *
     * @param files files to sort
     */
    override fun sortCloudFiles(files: List<RemoteFileBrowserItem>): List<RemoteFileBrowserItem> {
        Collections.sort(files, RemoteFileBrowserItemNameComparator(multiplier))
        return super.sortCloudFiles(files)
    }

    /**
     * Comparator for RemoteFileBrowserItems, sorts by name.
     */
    class RemoteFileBrowserItemNameComparator(private val multiplier: Int) : Comparator<RemoteFileBrowserItem> {
        private val alphanumComparator =
            AlphanumComparator<RemoteFileBrowserItem>()

        override fun compare(left: RemoteFileBrowserItem, right: RemoteFileBrowserItem): Int {
            return if (!left.isFile && !right.isFile) {
                return multiplier * alphanumComparator.compare(left.path, right.path)
            } else if (!left.isFile) {
                -1
            } else if (!right.isFile) {
                1
            } else {
                multiplier * alphanumComparator.compare(left.path, right.path)
            }
        }
    }
}
