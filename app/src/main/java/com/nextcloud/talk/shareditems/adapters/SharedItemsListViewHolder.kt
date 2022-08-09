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
import android.content.Intent
import android.text.format.Formatter
import android.view.View
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.SharedItemListBinding
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedLocationItem
import com.nextcloud.talk.shareditems.model.SharedOtherItem
import com.nextcloud.talk.shareditems.model.SharedPollItem
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DateUtils

class SharedItemsListViewHolder(
    override val binding: SharedItemListBinding,
    user: User,
    viewThemeUtils: ViewThemeUtils
) : SharedItemsViewHolder(binding, user, viewThemeUtils) {

    override val image: SimpleDraweeView
        get() = binding.fileImage
    override val clickTarget: View
        get() = binding.fileItem
    override val progressBar: ProgressBar
        get() = binding.progressBar

    override fun onBind(item: SharedFileItem) {
        super.onBind(item)

        binding.fileName.text = item.name
        binding.fileSize.text = item.fileSize.let {
            Formatter.formatShortFileSize(
                binding.fileSize.context,
                it
            )
        }
        binding.fileDate.text = DateUtils.getLocalDateTimeStringFromTimestamp(
            item.date * ONE_SECOND_IN_MILLIS
        )
    }

    override fun onBind(item: SharedPollItem, showPoll: (item: SharedItem, context: Context) -> Unit) {
        super.onBind(item, showPoll)

        binding.fileName.text = item.name
        binding.fileMetadata.visibility = View.GONE
        image.hierarchy.setPlaceholderImage(R.drawable.ic_baseline_bar_chart_24)
        image.setColorFilter(
            ContextCompat.getColor(image.context, R.color.high_emphasis_menu_icon),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        clickTarget.setOnClickListener {
            showPoll(item, it.context)
        }
    }

    override fun onBind(item: SharedLocationItem) {
        super.onBind(item)

        binding.fileName.text = item.name
        binding.fileMetadata.visibility = View.GONE
        image.hierarchy.setPlaceholderImage(R.drawable.ic_baseline_location_on_24)
        image.setColorFilter(
            ContextCompat.getColor(image.context, R.color.high_emphasis_menu_icon),
            android.graphics.PorterDuff.Mode.SRC_IN
        )

        clickTarget.setOnClickListener {

            val browserIntent = Intent(Intent.ACTION_VIEW, item.geoUri)
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            it.context.startActivity(browserIntent)
        }
    }

    override fun onBind(item: SharedOtherItem) {
        super.onBind(item)

        binding.fileName.text = item.name
        binding.fileMetadata.visibility = View.GONE
        image.hierarchy.setPlaceholderImage(R.drawable.ic_mimetype_file)
        image.setColorFilter(
            ContextCompat.getColor(image.context, R.color.high_emphasis_menu_icon),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    companion object {
        private const val ONE_SECOND_IN_MILLIS = 1000
    }
}
