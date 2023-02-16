/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.controllers.bottomsheet.items

import androidx.annotation.CheckResult
import androidx.recyclerview.widget.LinearLayoutManager
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.internal.list.DialogAdapter
import com.afollestad.materialdialogs.list.customListAdapter
import com.afollestad.materialdialogs.list.getListAdapter

typealias ListItemListener<IT> =
    ((dialog: MaterialDialog, index: Int, item: IT) -> Unit)?

@CheckResult
fun <IT : ListItemWithImage> MaterialDialog.listItemsWithImage(
    items: List<IT>,
    disabledIndices: IntArray? = null,
    waitForPositiveButton: Boolean = true,
    selection: ListItemListener<IT> = null
): MaterialDialog {
    if (getListAdapter() != null) {
        return updateListItemsWithImage(
            items = items,
            disabledIndices = disabledIndices
        )
    }

    val layoutManager = LinearLayoutManager(windowContext)
    return customListAdapter(
        adapter = ListIconDialogAdapter(
            dialog = this,
            items = items,
            disabledItems = disabledIndices,
            waitForPositiveButton = waitForPositiveButton,
            selection = selection
        ),
        layoutManager = layoutManager
    )
}

fun MaterialDialog.updateListItemsWithImage(
    items: List<ListItemWithImage>,
    disabledIndices: IntArray? = null
): MaterialDialog {
    val adapter = getListAdapter()
    check(adapter != null) {
        "updateGridItems(...) can't be used before you've created a bottom sheet grid dialog."
    }
    if (adapter is DialogAdapter<*, *>) {
        @Suppress("UNCHECKED_CAST")
        (adapter as DialogAdapter<ListItemWithImage, *>).replaceItems(items)

        if (disabledIndices != null) {
            adapter.disableItems(disabledIndices)
        }
    }
    return this
}
