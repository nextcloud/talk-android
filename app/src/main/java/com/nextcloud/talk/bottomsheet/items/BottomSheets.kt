/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.bottomsheet.items

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
