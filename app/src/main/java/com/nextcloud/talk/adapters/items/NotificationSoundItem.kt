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

import android.view.View
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import coil.api.load
import com.nextcloud.talk.R.drawable
import com.nextcloud.talk.R.id
import com.nextcloud.talk.R.layout
import com.nextcloud.talk.adapters.items.NotificationSoundItem.NotificationSoundItemViewHolder
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder

class NotificationSoundItem(
  val notificationSoundName: String,
  val notificationSoundUri: String?
) : AbstractFlexibleItem<NotificationSoundItemViewHolder>() {

  override fun equals(o: Any?): Boolean {
    return false
  }

  override fun getLayoutRes(): Int {
    return layout.rv_item_notification_sound
  }

  override fun createViewHolder(
    view: View,
    adapter: FlexibleAdapter<IFlexible<*>?>
  ): NotificationSoundItemViewHolder {
    return NotificationSoundItemViewHolder(view, adapter)
  }

  override fun bindViewHolder(
    adapter: FlexibleAdapter<IFlexible<*>?>,
    holder: NotificationSoundItemViewHolder,
    position: Int,
    payloads: List<Any>
  ) {
    holder.notificationName!!.text = notificationSoundName
    if (adapter.isSelected(position)) {
      holder.checkedImageView!!.visibility = View.VISIBLE
    } else {
      holder.checkedImageView!!.visibility = View.GONE
    }

    if (position == 0) {
      holder.imageView!!.load(drawable.ic_stop_white_24dp)
    } else {
      holder.imageView!!.load(drawable.ic_play_circle_outline_white_24dp)
    }
  }

  class NotificationSoundItemViewHolder(
    view: View,
    adapter: FlexibleAdapter<*>
  ) : FlexibleViewHolder(view, adapter) {
    @JvmField
    @BindView(id.notificationNameTextView)
    var notificationName: TextView? = null
    @JvmField
    @BindView(id.imageView)
    var imageView: ImageView? = null
    @JvmField
    @BindView(id.checkedImageView)
    var checkedImageView: ImageView? = null

    /**
     * Default constructor.
     */

    init {
      ButterKnife.bind(this, view)
    }
  }

}