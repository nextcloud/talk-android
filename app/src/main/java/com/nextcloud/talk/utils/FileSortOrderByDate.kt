/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021-2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Sven R. Kunze
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import java.util.Collections

class FileSortOrderByDate internal constructor(name: String, ascending: Boolean) : FileSortOrder(name, ascending) {
    /**
     * Sorts list by Date.
     *
     * @param files list of files to sort
     */
    override fun sortCloudFiles(files: List<RemoteFileBrowserItem>): List<RemoteFileBrowserItem> {
        Collections.sort(files, RemoteFileBrowserItemDateComparator(multiplier))
        return super.sortCloudFiles(files)
    }

    /**
     * Comparator for RemoteFileBrowserItems, sorts by modified timestamp.
     */
    class RemoteFileBrowserItemDateComparator(private val multiplier: Int) : Comparator<RemoteFileBrowserItem> {

        override fun compare(left: RemoteFileBrowserItem, right: RemoteFileBrowserItem): Int =
            multiplier * left.modifiedTimestamp.compareTo(right.modifiedTimestamp)
    }
}
