/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
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
    const val VIEW_TYPE: Int = R.layout.rv_item_load_more

    class ViewHolder(view: View, adapter: FlexibleAdapter<*>) :
        FlexibleViewHolder(view, adapter) {
        var binding: RvItemLoadMoreBinding

        init {
            binding = RvItemLoadMoreBinding.bind(view)
        }
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

    override fun getItemViewType(): Int {
        return VIEW_TYPE
    }

    override fun equals(other: Any?): Boolean {
        return other is LoadMoreResultsItem
    }

    override fun hashCode(): Int {
        return 0
    }
}
