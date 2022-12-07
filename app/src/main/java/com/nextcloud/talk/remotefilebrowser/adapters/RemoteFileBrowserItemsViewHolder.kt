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

import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.remotefilebrowser.SelectionInterface
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.utils.DrawableUtils

abstract class RemoteFileBrowserItemsViewHolder(
    open val binding: ViewBinding,
    val mimeTypeSelectionFilter: String? = null,
    val currentUser: User,
    val selectionInterface: SelectionInterface,
) : RecyclerView.ViewHolder(binding.root) {

    abstract val fileIcon: ImageView

    open fun onBind(item: RemoteFileBrowserItem) {
        fileIcon.setImageResource(DrawableUtils.getDrawableResourceIdForMimeType(item.mimeType))
    }
}
