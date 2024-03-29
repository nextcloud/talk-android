/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.adapters

import android.content.Context
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.extensions.loadImage
import com.nextcloud.talk.shareditems.model.SharedDeckCardItem
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedLocationItem
import com.nextcloud.talk.shareditems.model.SharedOtherItem
import com.nextcloud.talk.shareditems.model.SharedPollItem
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.FileViewerUtils

abstract class SharedItemsViewHolder(
    open val binding: ViewBinding,
    internal val user: User,
    private val viewThemeUtils: ViewThemeUtils
) : RecyclerView.ViewHolder(binding.root) {

    companion object {
        private val TAG = SharedItemsViewHolder::class.simpleName
    }

    abstract val image: ImageView
    abstract val clickTarget: View
    abstract val progressBar: ProgressBar

    open fun onBind(item: SharedFileItem) {
        val placeholder = viewThemeUtils.talk.getPlaceholderImage(image.context, item.mimeType)
        if (item.previewAvailable) {
            image.loadImage(
                item.previewLink,
                user,
                placeholder
            )
        } else {
            image.setImageDrawable(placeholder)
        }

        /*
        The FileViewerUtils forces us to do things at this points which should be done separated in the activity and
        the view model.

        This should be done after a refactoring of FileViewerUtils.
         */
        val fileViewerUtils = FileViewerUtils(image.context, user)

        clickTarget.setOnClickListener {
            fileViewerUtils.openFile(
                FileViewerUtils.FileInfo(item.id, item.name, item.fileSize),
                item.path,
                item.link,
                item.mimeType,
                FileViewerUtils.ProgressUi(
                    progressBar,
                    null,
                    image
                ),
                true
            )
        }

        fileViewerUtils.resumeToUpdateViewsByProgress(
            item.name,
            item.id,
            item.mimeType,
            true,
            FileViewerUtils.ProgressUi(progressBar, null, image)
        )
    }

    open fun onBind(item: SharedPollItem, showPoll: (item: SharedItem, context: Context) -> Unit) {}

    open fun onBind(item: SharedLocationItem) {}

    open fun onBind(item: SharedOtherItem) {}

    open fun onBind(item: SharedDeckCardItem) {}
}
