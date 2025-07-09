/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <infoi@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.items

import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.RvItemTitleHeaderBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import java.util.Objects

open class GenericTextHeaderItem(title: String, viewThemeUtils: ViewThemeUtils) :
    AbstractHeaderItem<GenericTextHeaderItem.HeaderViewHolder>() {
    val model: String
    private val viewThemeUtils: ViewThemeUtils

    init {
        isHidden = false
        isSelectable = false
        this.model = title
        this.viewThemeUtils = viewThemeUtils
    }

    override fun equals(o: Any?): Boolean {
        if (o is GenericTextHeaderItem) {
            return model == o.model
        }
        return false
    }

    override fun hashCode(): Int = Objects.hash(model)

    override fun getLayoutRes(): Int = R.layout.rv_item_title_header

    override fun createViewHolder(
        view: View?,
        adapter: FlexibleAdapter<IFlexible<RecyclerView.ViewHolder>>?
    ): HeaderViewHolder = HeaderViewHolder(view, adapter)

    override fun bindViewHolder(
        adapter: FlexibleAdapter<IFlexible<*>?>?,
        holder: HeaderViewHolder,
        position: Int,
        payloads: List<Any>
    ) {
        if (payloads.size > 0) {
            Log.d(TAG, "We have payloads, so ignoring!")
        } else {
            holder.binding.titleTextView.text = model
            viewThemeUtils.platform.colorPrimaryTextViewElement(holder.binding.titleTextView)
        }
    }

    class HeaderViewHolder(view: View?, adapter: FlexibleAdapter<*>?) : FlexibleViewHolder(view, adapter, true) {
        var binding: RvItemTitleHeaderBinding =
            RvItemTitleHeaderBinding.bind(view!!)
    }

    companion object {
        private const val TAG = "GenericTextHeaderItem"
    }
}
