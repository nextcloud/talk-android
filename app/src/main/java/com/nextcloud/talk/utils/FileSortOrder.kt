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

import android.text.TextUtils
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import java.util.Collections

/**
 * Sort order
 */
open class FileSortOrder(var name: String, var isAscending: Boolean) {
    companion object {
        const val sort_a_to_z_id = "sort_a_to_z"
        const val sort_z_to_a_id = "sort_z_to_a"
        const val sort_old_to_new_id = "sort_old_to_new"
        const val sort_new_to_old_id = "sort_new_to_old"
        const val sort_small_to_big_id = "sort_small_to_big"
        const val sort_big_to_small_id = "sort_big_to_small"

        val sort_a_to_z: FileSortOrder = FileSortOrderByName(sort_a_to_z_id, true)
        val sort_z_to_a: FileSortOrder = FileSortOrderByName(sort_z_to_a_id, false)
        val sort_old_to_new: FileSortOrder = FileSortOrderByDate(sort_old_to_new_id, true)
        val sort_new_to_old: FileSortOrder = FileSortOrderByDate(sort_new_to_old_id, false)
        val sort_small_to_big: FileSortOrder = FileSortOrderBySize(sort_small_to_big_id, true)
        val sort_big_to_small: FileSortOrder = FileSortOrderBySize(sort_big_to_small_id, false)

        val sortOrders: Map<String, FileSortOrder> = mapOf(
            sort_a_to_z.name to sort_a_to_z,
            sort_z_to_a.name to sort_z_to_a,
            sort_old_to_new.name to sort_old_to_new,
            sort_new_to_old.name to sort_new_to_old,
            sort_small_to_big.name to sort_small_to_big,
            sort_big_to_small.name to sort_big_to_small,
        )

        fun getFileSortOrder(key: String?): FileSortOrder {
            return if (TextUtils.isEmpty(key) || !sortOrders.containsKey(key)) {
                sort_a_to_z
            } else {
                sortOrders[key]!!
            }
        }

        /**
         * Sorts list by Favourites.
         *
         * @param files files to sort
         */
        fun sortCloudFilesByFavourite(files: List<RemoteFileBrowserItem>): List<RemoteFileBrowserItem> {
            Collections.sort(files, RemoteFileBrowserItemFavoriteComparator())
            return files
        }
    }

    open fun sortCloudFiles(files: List<RemoteFileBrowserItem>): List<RemoteFileBrowserItem> {
        return sortCloudFilesByFavourite(files)
    }

    val multiplier : Int
        get() = if (isAscending) 1 else -1

    /**
     * Comparator for RemoteFileBrowserItems, sorts favorite state.
     */
    class RemoteFileBrowserItemFavoriteComparator : Comparator<RemoteFileBrowserItem> {
        override fun compare(left: RemoteFileBrowserItem, right: RemoteFileBrowserItem): Int {
            return if (left.isFavorite && right.isFavorite) {
                0
            } else if (left.isFavorite) {
                -1
            } else if (right.isFavorite) {
                1
            } else {
                0
            }
        }
    }
}
