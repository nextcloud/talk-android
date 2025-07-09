/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.items

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.RvItemLoadMoreBinding
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder

object LoadMoreResultsItem :
    AbstractFlexibleItem<LoadMoreResultsItem.ViewHolder>(),
    IFilterable<String> {

    // layout is used as view type for uniqueness
    const val VIEW_TYPE = FlexibleItemViewType.LOAD_MORE_RESULTS_ITEM

    class ViewHolder(view: View, adapter: FlexibleAdapter<*>) : FlexibleViewHolder(view, adapter) {
        var binding: RvItemLoadMoreBinding = RvItemLoadMoreBinding.bind(view)
    }

    override fun getLayoutRes(): Int = R.layout.rv_item_load_more

    override fun createViewHolder(
        view: View,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>
    ): ViewHolder = ViewHolder(view, adapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>,
        holder: ViewHolder,
        position: Int,
        payloads: MutableList<Any>?
    ) {
        // nothing, it's immutable
    }

    override fun filter(constraint: String?): Boolean = true

    override fun getItemViewType(): Int = VIEW_TYPE

    override fun equals(other: Any?): Boolean = other is LoadMoreResultsItem

    override fun hashCode(): Int = 0
}
