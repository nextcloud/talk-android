/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.bottomsheet.items

import android.widget.ImageView
import androidx.annotation.DrawableRes

interface ListItemWithImage {
    val title: String
    fun populateIcon(imageView: ImageView)
}

data class BasicListItemWithImage(@DrawableRes val iconRes: Int, override val title: String) : ListItemWithImage {

    override fun populateIcon(imageView: ImageView) {
        imageView.setImageResource(iconRes)
    }
}
