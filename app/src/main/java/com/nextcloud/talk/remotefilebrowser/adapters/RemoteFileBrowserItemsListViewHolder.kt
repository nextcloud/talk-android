/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.remotefilebrowser.adapters

import android.text.format.Formatter
import android.view.View
import android.widget.ImageView
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemBrowserFileBinding
import com.nextcloud.talk.extensions.loadImage
import com.nextcloud.talk.remotefilebrowser.SelectionInterface
import com.nextcloud.talk.remotefilebrowser.model.RemoteFileBrowserItem
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.Mimetype.FOLDER

class RemoteFileBrowserItemsListViewHolder(
    override val binding: RvItemBrowserFileBinding,
    mimeTypeSelectionFilter: String?,
    currentUser: User,
    selectionInterface: SelectionInterface,
    private val viewThemeUtils: ViewThemeUtils,
    private val dateUtils: DateUtils,
    onItemClicked: (Int) -> Unit
) : RemoteFileBrowserItemsViewHolder(binding, mimeTypeSelectionFilter, currentUser, selectionInterface) {

    override val fileIcon: ImageView
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

        val placeholder = viewThemeUtils.talk.getPlaceholderImage(binding.root.context, item.mimeType)

        if (item.hasPreview) {
            val path = ApiUtils.getUrlForFilePreviewWithRemotePath(
                currentUser.baseUrl!!,
                item.path,
                fileIcon.context.resources.getDimensionPixelSize(R.dimen.small_item_height)
            )
            if (path.isNotEmpty()) {
                fileIcon.loadImage(path, currentUser, placeholder)
            }
        } else {
            fileIcon.setImageDrawable(placeholder)
        }

        binding.filenameTextView.text = item.displayName
        binding.fileModifiedInfo.text = String.format(
            binding.fileModifiedInfo.context.getString(R.string.nc_last_modified),
            Formatter.formatShortFileSize(binding.fileModifiedInfo.context, item.size),
            dateUtils.getLocalDateTimeStringFromTimestamp(item.modifiedTimestamp)
        )

        binding.selectFileCheckbox.isChecked = selectionInterface.isPathSelected(item.path!!)
    }

    private fun setSelectability() {
        if (selectable) {
            binding.selectFileCheckbox.visibility = View.VISIBLE
            viewThemeUtils.platform.themeCheckbox(binding.selectFileCheckbox)
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
