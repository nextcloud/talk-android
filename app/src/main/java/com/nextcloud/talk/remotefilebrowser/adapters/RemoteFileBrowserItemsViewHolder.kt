/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
    val selectionInterface: SelectionInterface
) : RecyclerView.ViewHolder(binding.root) {

    abstract val fileIcon: ImageView

    open fun onBind(item: RemoteFileBrowserItem) {
        fileIcon.setImageResource(DrawableUtils.getDrawableResourceIdForMimeType(item.mimeType))
    }
}
