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

package com.nextcloud.talk.components.filebrowser.adapters.items

import android.content.Context
import android.text.format.Formatter
import android.view.View
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import coil.api.load
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.filebrowser.models.BrowserFile
import com.nextcloud.talk.interfaces.SelectionInterface
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.utils.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DrawableUtils
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem
import eu.davidea.flexibleadapter.items.IFilterable
import eu.davidea.flexibleadapter.items.IFlexible
import eu.davidea.viewholders.FlexibleViewHolder
import org.koin.core.KoinComponent
import org.koin.core.inject

class BrowserFileItem(
  val model: BrowserFile,
  private val activeUser: UserNgEntity,
  private val selectionInterface: SelectionInterface
) : AbstractFlexibleItem<BrowserFileItem.ViewHolder>(), IFilterable<String>, KoinComponent {
  val context: Context by inject()
  var isSelected: Boolean = false

  override fun equals(other: Any?): Boolean {
    if (other is BrowserFileItem) {
      val inItem = other as BrowserFileItem?
      return model.path == inItem!!.model.path
    }

    return false
  }

  override fun getLayoutRes(): Int {
    return R.layout.rv_item_browser_file
  }

  override fun createViewHolder(
    view: View,
    adapter: FlexibleAdapter<IFlexible<*>>
  ): ViewHolder {
    return ViewHolder(view, adapter)
  }

  override fun bindViewHolder(
    adapter: FlexibleAdapter<IFlexible<*>>,
    holder: ViewHolder,
    position: Int,
    payloads: List<Any>
  ) {

    if (model.encrypted) {
      holder.fileEncryptedImageView!!.visibility = View.VISIBLE
      holder.itemView.isEnabled = false
      holder.itemView.alpha = 0.38f
    } else {
      holder.fileEncryptedImageView!!.visibility = View.GONE
      holder.itemView.isEnabled = true
      holder.itemView.alpha = 1.0f
    }

    if (model.favorite) {
      holder.fileFavoriteImageView!!.visibility = View.VISIBLE
    } else {
      holder.fileFavoriteImageView!!.visibility = View.GONE
    }

    holder.fileIconImageView!!.load(
        context?.getDrawable(
            DrawableUtils
                .getDrawableResourceIdForMimeType(model.mimeType)
        )
    )

    if (model.hasPreview) {
      val path = ApiUtils.getUrlForFilePreviewWithRemotePath(
          activeUser.baseUrl,
          model.path,
          context!!.resources.getDimensionPixelSize(R.dimen.small_item_height)
      )

      if (path.length > 0) {
        holder.fileIconImageView!!.load(path) {
          addHeader("Authorization", activeUser.getCredentials())
        }
      }
    }

    holder.filenameTextView!!.text = model.displayName
    holder.fileModifiedTextView!!.text = String.format(
        context!!.getString(R.string.nc_last_modified),
        Formatter.formatShortFileSize(context, model.size),
        DateUtils.getLocalDateTimeStringFromTimestamp(
            model.modifiedTimestamp
        )
    )
    isSelected = selectionInterface.isPathSelected(model.path)
    holder.selectFileCheckbox!!.isChecked = isSelected

    if (!model.encrypted) {
      holder.selectFileCheckbox!!.setOnClickListener { v ->
        if ((v as CheckBox).isChecked != isSelected) {
          isSelected = v.isChecked
          selectionInterface.toggleBrowserItemSelection(model.path)
        }
      }
    }

    holder.filenameTextView!!.isSelected = true
    holder.fileModifiedTextView!!.isSelected = true
  }

  override fun filter(constraint: String): Boolean {
    return false
  }

  class ViewHolder(
    view: View,
    adapter: FlexibleAdapter<*>
  ) : FlexibleViewHolder(view, adapter) {

    @JvmField
    @BindView(R.id.file_icon)
    var fileIconImageView: ImageView? = null
    @JvmField
    @BindView(R.id.file_modified_info)
    var fileModifiedTextView: TextView? = null
    @JvmField
    @BindView(R.id.filename_text_view)
    var filenameTextView: TextView? = null
    @JvmField
    @BindView(R.id.select_file_checkbox)
    var selectFileCheckbox: CheckBox? = null
    @JvmField
    @BindView(R.id.fileEncryptedImageView)
    var fileEncryptedImageView: ImageView? = null
    @JvmField
    @BindView(R.id.fileFavoriteImageView)
    var fileFavoriteImageView: ImageView? = null

    init {
      ButterKnife.bind(this, view)
    }
  }
}
