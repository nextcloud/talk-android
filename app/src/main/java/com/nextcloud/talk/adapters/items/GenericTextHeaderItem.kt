/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.adapters.items

import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import butterknife.BindView
import butterknife.ButterKnife
import com.nextcloud.talk.R
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractHeaderItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder

class GenericTextHeaderItem(val model: String) : AbstractHeaderItem<GenericTextHeaderItem.HeaderViewHolder>() {
  override fun createViewHolder(
    view: View,
    adapter: FlexibleAdapter<IFlexible<ViewHolder>>
  ): HeaderViewHolder {
    return HeaderViewHolder(view, adapter)
  }

  init {
    isHidden = false
    isSelectable = false
  }

  override fun equals(other: Any?): Boolean {
    if (other is GenericTextHeaderItem) {
      val inItem = other as GenericTextHeaderItem?
      return model == inItem!!.model
    }
    return false
  }

  override fun getLayoutRes(): Int {
    return R.layout.rv_item_title_header
  }

  override fun bindViewHolder(
    adapter: FlexibleAdapter<IFlexible<*>>,
    holder: HeaderViewHolder,
    position: Int,
    payloads: List<Any>
  ) {
    if (payloads.size > 0) {
      Log.d(TAG, "We have payloads, so ignoring!")
    } else {
      holder.titleTextView!!.text = model
    }
  }

  class HeaderViewHolder
  /**
   * Default constructor.
   */
  (
    view: View,
    adapter: FlexibleAdapter<*>
  ) : FlexibleViewHolder(view, adapter, true) {

    @JvmField
    @BindView(R.id.title_text_view)
    var titleTextView: TextView? = null

    init {
      ButterKnife.bind(this, view)
    }
  }

  companion object {
    private val TAG = "GenericTextHeaderItem"
  }
}
