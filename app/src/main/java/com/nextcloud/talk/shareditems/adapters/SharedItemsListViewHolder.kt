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

import android.text.format.Formatter
import android.view.View
import android.widget.ProgressBar
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.databinding.SharedItemListBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.utils.DateUtils

class SharedItemsListViewHolder(
    override val binding: SharedItemListBinding,
    userEntity: UserEntity
) : SharedItemsViewHolder(binding, userEntity) {

    override val image: SimpleDraweeView
        get() = binding.fileImage
    override val clickTarget: View
        get() = binding.fileItem
    override val progressBar: ProgressBar
        get() = binding.progressBar

    override fun onBind(item: SharedItem) {

        super.onBind(item)

        binding.fileName.text = item.name
        binding.fileSize.text = item.fileSize?.let {
            Formatter.formatShortFileSize(
                binding.fileSize.context,
                it
            )
        }
        binding.fileDate.text = DateUtils.getLocalDateTimeStringFromTimestamp(
            item.date * ONE_SECOND_IN_MILLIS
        )
    }

    companion object {
        private const val ONE_SECOND_IN_MILLIS = 1000
    }
}
