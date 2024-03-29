/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.adapters

import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.SharedItemGridBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils

class SharedItemsGridViewHolder(
    override val binding: SharedItemGridBinding,
    user: User,
    viewThemeUtils: ViewThemeUtils
) : SharedItemsViewHolder(binding, user, viewThemeUtils) {

    override val image: ImageView
        get() = binding.image
    override val clickTarget: View
        get() = binding.image
    override val progressBar: ProgressBar
        get() = binding.progressBar
}
