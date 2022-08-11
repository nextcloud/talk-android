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

import android.text.format.Formatter
import android.view.View
import autodagger.AutoInjector
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemBrowserFileBinding
import com.nextcloud.talk.remotefilebrowser.SelectionInterface
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateUtils.getLocalDateTimeStringFromTimestamp
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.Mimetype.FOLDER

@AutoInjector(NextcloudTalkApplication::class)
class RemoteFileBrowserItemsListViewHolder(
    override val binding: RvItemBrowserFileBinding,
    mimeTypeSelectionFilter: String?,
    currentUser: User,
    selectionInterface: SelectionInterface,
    private val viewThemeUtils: ViewThemeUtils,
    onItemClicked: (Int) -> Unit
) : RemoteFileBrowserItemsViewHolder(binding, mimeTypeSelectionFilter, currentUser, selectionInterface) {

    override val fileIcon: SimpleDraweeView
        get() = binding.fileIcon

    private var selectable: Boolean = true
    private var clickable: Boolean = true

    init {
        itemView.setOnClickListener {
            if (clickable) {
                onItemClicked(bindingAdapterPosition)
                if (selectable) {
                    binding.selectFileCheckbox.toggle()
                }
            }
        }
    }

    override fun onBind(item: RemoteFileBrowserItem) {
        super.onBind(item)

        binding.fileIcon.controller = null
        if (!item.isAllowedToReShare || item.isEncrypted) {
            binding.root.isEnabled = false
            binding.root.alpha = DISABLED_ALPHA
        } else {
            binding.root.isEnabled = true
            binding.root.alpha = ENABLED_ALPHA
        }

        binding.fileEncryptedImageView.visibility =
            if (item.isEncrypted) {
                View.VISIBLE
            } else {
                View.GONE
            }

        binding.fileFavoriteImageView.visibility =
            if (item.isFavorite) {
                View.VISIBLE
            } else {
                View.GONE
            }

        calculateSelectability(item)
        calculateClickability(item, selectable)
        setSelectability()

        binding.fileIcon
            .hierarchy
            .setPlaceholderImage(
                viewThemeUtils.getPlaceholderImage(binding.root.context, item.mimeType)
            )

        if (item.hasPreview) {
            val path = ApiUtils.getUrlForFilePreviewWithRemotePath(
                currentUser.baseUrl,
                item.path,
                binding.fileIcon.context.resources.getDimensionPixelSize(R.dimen.small_item_height)
            )
            if (path.isNotEmpty()) {
                val draweeController: DraweeController = Fresco.newDraweeControllerBuilder()
                    .setAutoPlayAnimations(true)
                    .setImageRequest(DisplayUtils.getImageRequestForUrl(path))
                    .build()
                binding.fileIcon.controller = draweeController
            }
        }

        binding.filenameTextView.text = item.displayName
        binding.fileModifiedInfo.text = String.format(
            binding.fileModifiedInfo.context.getString(R.string.nc_last_modified),
            Formatter.formatShortFileSize(binding.fileModifiedInfo.context, item.size),
            getLocalDateTimeStringFromTimestamp(item.modifiedTimestamp)
        )

        binding.selectFileCheckbox.isChecked = selectionInterface.isPathSelected(item.path!!)
    }

    private fun setSelectability() {
        if (selectable) {
            binding.selectFileCheckbox.visibility = View.VISIBLE
            viewThemeUtils.themeCheckbox(binding.selectFileCheckbox)
        } else {
            binding.selectFileCheckbox.visibility = View.GONE
        }
    }

    private fun calculateSelectability(item: RemoteFileBrowserItem) {
        selectable = item.isFile &&
            (mimeTypeSelectionFilter == null || item.mimeType?.startsWith(mimeTypeSelectionFilter) == true) &&
            (item.isAllowedToReShare && !item.isEncrypted)
    }

    private fun calculateClickability(item: RemoteFileBrowserItem, selectableItem: Boolean) {
        clickable = selectableItem || FOLDER == item.mimeType
    }

    companion object {
        private const val DISABLED_ALPHA: Float = 0.38f
        private const val ENABLED_ALPHA: Float = 1.0f
    }
}
