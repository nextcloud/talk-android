/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.remotefilebrowser.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemBrowserFileBinding
import com.nextcloud.talk.remotefilebrowser.SelectionInterface
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DateUtils

class RemoteFileBrowserItemsAdapter(
    private val showGrid: Boolean = false,
    private val mimeTypeSelectionFilter: String? = null,
    private val user: User,
    private val selectionInterface: SelectionInterface,
    private val viewThemeUtils: ViewThemeUtils,
    private val dateUtils: DateUtils,
    private val onItemClicked: (RemoteFileBrowserItem) -> Unit
) : RecyclerView.Adapter<RemoteFileBrowserItemsViewHolder>() {

    var items: List<RemoteFileBrowserItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RemoteFileBrowserItemsViewHolder =
        if (showGrid) {
            RemoteFileBrowserItemsListViewHolder(
                RvItemBrowserFileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                mimeTypeSelectionFilter,
                user,
                selectionInterface,
                viewThemeUtils,
                dateUtils
            ) {
                onItemClicked(items[it])
            }
        } else {
            RemoteFileBrowserItemsListViewHolder(
                RvItemBrowserFileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                mimeTypeSelectionFilter,
                user,
                selectionInterface,
                viewThemeUtils,
                dateUtils
            ) {
                onItemClicked(items[it])
            }
        }

    override fun onBindViewHolder(holder: RemoteFileBrowserItemsViewHolder, position: Int) {
        holder.onBind(items[position])
    }

    override fun getItemCount(): Int = items.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateDataSet(browserItems: List<RemoteFileBrowserItem>) {
        items = browserItems
        notifyDataSetChanged()
    }
}
