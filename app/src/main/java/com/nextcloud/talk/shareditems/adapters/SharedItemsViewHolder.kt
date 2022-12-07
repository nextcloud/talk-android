/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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
                )
            )
        }

        fileViewerUtils.resumeToUpdateViewsByProgress(
            item.name,
            item.id,
            item.mimeType,
            FileViewerUtils.ProgressUi(progressBar, null, image)
        )
    }

    open fun onBind(item: SharedPollItem, showPoll: (item: SharedItem, context: Context) -> Unit) {}

    open fun onBind(item: SharedLocationItem) {}

    open fun onBind(item: SharedOtherItem) {}

    open fun onBind(item: SharedDeckCardItem) {}
}
