/*
 * Nextcloud Talk application
 *
 * @author Sven R. Kunze
 * @author Andy Scherzinger
 * Copyright (C) 2021-2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Sven R. Kunze
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
package com.nextcloud.talk.utils

import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import third_parties.daveKoeller.AlphanumComparator
import java.util.Collections

/**
 * Created by srkunze on 28.08.17.
 */
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

        override fun compare(left: RemoteFileBrowserItem, right: RemoteFileBrowserItem): Int {
            return if (!left.isFile && !right.isFile) {
                return multiplier * AlphanumComparator<Any?>().compare(left, right)
            } else if (!left.isFile) {
                -1
            } else if (!right.isFile) {
                1
            } else {
                multiplier * AlphanumComparator<Any?>().compare(left, right)
            }
        }
    }
}
