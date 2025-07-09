/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.items

import android.view.View
import com.nextcloud.talk.R
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder

class SpacerItem(private val height: Int) : AbstractFlexibleItem<SpacerItem.ViewHolder>() {

    override fun getLayoutRes(): Int = R.layout.item_spacer

    override fun createViewHolder(view: View?, adapter: FlexibleAdapter<IFlexible<*>?>?): ViewHolder =
        ViewHolder(view!!, adapter!!)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<*>?>?,
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>?
    ) {
        holder.itemView.layoutParams.height = height
    }

    override fun equals(other: Any?) = other is SpacerItem

    override fun hashCode(): Int = 0

    class ViewHolder(view: View, adapter: FlexibleAdapter<*>) : FlexibleViewHolder(view, adapter)
}
