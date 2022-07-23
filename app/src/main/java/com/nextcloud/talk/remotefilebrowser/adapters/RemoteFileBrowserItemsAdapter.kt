/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.remotefilebrowser.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemBrowserFileBinding
import com.nextcloud.talk.remotefilebrowser.SelectionInterface
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem

class RemoteFileBrowserItemsAdapter(
    private val showGrid: Boolean = false,
    private val mimeTypeSelectionFilter: String? = null,
    private val user: User,
    private val selectionInterface: SelectionInterface,
    private val onItemClicked: (RemoteFileBrowserItem) -> Unit
) : RecyclerView.Adapter<RemoteFileBrowserItemsViewHolder>() {

    var items: List<RemoteFileBrowserItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RemoteFileBrowserItemsViewHolder {

        return if (showGrid) {
            RemoteFileBrowserItemsListViewHolder(
                RvItemBrowserFileBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                mimeTypeSelectionFilter,
                user,
                selectionInterface
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
                selectionInterface
            ) {
                onItemClicked(items[it])
            }
        }
    }

    override fun onBindViewHolder(holder: RemoteFileBrowserItemsViewHolder, position: Int) {
        holder.onBind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateDataSet(browserItems: List<RemoteFileBrowserItem>) {
        items = browserItems
        notifyDataSetChanged()
    }
}
