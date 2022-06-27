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

import android.content.Context
import android.text.SpannableString
import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.databinding.RvItemSearchMessageBinding
import com.nextcloud.talk.models.domain.SearchMessageEntry
import com.nextcloud.talk.utils.DisplayUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.flexibleadapter.items.ISectionable
import eu.davidea.viewholders.FlexibleViewHolder

data class MessageResultItem constructor(
    private val context: Context,
    private val currentUser: User,
    val messageEntry: SearchMessageEntry,
    private val showHeader: Boolean = false
) :
    AbstractFlexibleItem<MessageResultItem.ViewHolder>(),
    IFilterable<String>,
    ISectionable<MessageResultItem.ViewHolder, GenericTextHeaderItem> {

    class ViewHolder(view: View, adapter: FlexibleAdapter<*>) :
        FlexibleViewHolder(view, adapter) {
        var binding: RvItemSearchMessageBinding

        init {
            binding = RvItemSearchMessageBinding.bind(view)
        }
    }

    override fun getLayoutRes(): Int = R.layout.rv_item_search_message

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
        holder.binding.conversationTitle.text = messageEntry.title
        bindMessageExcerpt(holder)
        loadImage(holder)
    }

    private fun bindMessageExcerpt(holder: ViewHolder) {
        val messageSpannable = SpannableString(messageEntry.messageExcerpt)
        val highlightColor = ContextCompat.getColor(context, R.color.colorPrimary)
        val highlightedSpan = DisplayUtils.searchAndColor(messageSpannable, messageEntry.searchTerm, highlightColor)
        holder.binding.messageExcerpt.text = highlightedSpan
    }

    private fun loadImage(holder: ViewHolder) {
        DisplayUtils.loadAvatarPlaceholder(holder.binding.thumbnail)
        if (messageEntry.thumbnailURL != null) {
            val imageRequest = DisplayUtils.getImageRequestForUrlNew(
                messageEntry.thumbnailURL,
                currentUser
            )
            DisplayUtils.loadImage(holder.binding.thumbnail, imageRequest)
        }
    }

    override fun filter(constraint: String?): Boolean = true

    override fun getItemViewType(): Int {
        return VIEW_TYPE
    }

    companion object {
        // layout is used as view type for uniqueness
        const val VIEW_TYPE: Int = R.layout.rv_item_search_message
    }

    override fun getHeader(): GenericTextHeaderItem = MessagesTextHeaderItem(context)
        .apply {
            isHidden = showHeader // FlexibleAdapter needs this hack for some reason
        }

    override fun setHeader(header: GenericTextHeaderItem?) {
        // nothing, header is always the same
    }
}
