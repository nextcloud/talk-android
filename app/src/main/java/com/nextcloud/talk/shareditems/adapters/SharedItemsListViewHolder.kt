/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.adapters

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import coil.load
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.SharedItemListBinding
import com.nextcloud.talk.shareditems.model.SharedDeckCardItem
import com.nextcloud.talk.shareditems.model.SharedFileItem
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.shareditems.model.SharedLocationItem
import com.nextcloud.talk.shareditems.model.SharedOtherItem
import com.nextcloud.talk.shareditems.model.SharedPollItem
import com.nextcloud.talk.ui.theme.ViewThemeUtils

class SharedItemsListViewHolder(
    override val binding: SharedItemListBinding,
    user: User,
    viewThemeUtils: ViewThemeUtils
) : SharedItemsViewHolder(binding, user, viewThemeUtils) {

    override val image: ImageView
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
        binding.fileDate.text = item.dateTime
        binding.actor.text = item.actorName
    }

    override fun onBind(item: SharedPollItem, showPoll: (item: SharedItem, context: Context) -> Unit) {
        super.onBind(item, showPoll)

        binding.fileName.text = item.name
        binding.fileSize.visibility = View.GONE
        binding.separator1.visibility = View.GONE
        binding.fileDate.text = item.dateTime
        binding.actor.text = item.actorName
        image.load(R.drawable.ic_baseline_bar_chart_24)
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
        binding.fileSize.visibility = View.GONE
        binding.separator1.visibility = View.GONE
        binding.fileDate.text = item.dateTime
        binding.actor.text = item.actorName
        image.load(R.drawable.ic_baseline_location_on_24)
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
        binding.fileSize.visibility = View.GONE
        binding.separator1.visibility = View.GONE
        binding.fileDate.text = item.dateTime
        binding.actor.text = item.actorName
        image.load(R.drawable.ic_mimetype_file)
        image.setColorFilter(
            ContextCompat.getColor(image.context, R.color.high_emphasis_menu_icon),
            android.graphics.PorterDuff.Mode.SRC_IN
        )
    }

    override fun onBind(item: SharedDeckCardItem) {
        super.onBind(item)

        binding.fileName.text = item.name
        binding.fileSize.visibility = View.GONE
        binding.separator1.visibility = View.GONE
        binding.fileDate.text = item.dateTime
        binding.actor.text = item.actorName
        image.load(R.drawable.ic_baseline_deck_24)
        image.setColorFilter(
            ContextCompat.getColor(image.context, R.color.high_emphasis_menu_icon),
            android.graphics.PorterDuff.Mode.SRC_IN
        )

        clickTarget.setOnClickListener {
            val browserIntent = Intent(Intent.ACTION_VIEW, item.link)
            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            it.context.startActivity(browserIntent)
        }
    }
}
