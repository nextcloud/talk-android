/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.moyn.talk.controllers.bottomsheet.items

import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.WhichButton
import com.afollestad.materialdialogs.actions.hasActionButton
import com.afollestad.materialdialogs.actions.hasActionButtons
import com.afollestad.materialdialogs.internal.list.DialogAdapter
import com.afollestad.materialdialogs.internal.rtl.RtlTextView
import com.afollestad.materialdialogs.list.getItemSelector
import com.afollestad.materialdialogs.utils.MDUtil.inflate
import com.afollestad.materialdialogs.utils.MDUtil.maybeSetTextColor
import com.moyn.talk.R

private const val KEY_ACTIVATED_INDEX = "activated_index"

internal class ListItemViewHolder(
    itemView: View,
    private val adapter: ListIconDialogAdapter<*>
) : RecyclerView.ViewHolder(itemView), View.OnClickListener {
    init {
        itemView.setOnClickListener(this)
    }

    val iconView: ImageView = itemView.findViewById(R.id.icon)
    val titleView: RtlTextView = itemView.findViewById(R.id.title)

    override fun onClick(view: View) = adapter.itemClicked(adapterPosition)
}

internal class ListIconDialogAdapter<IT : ListItemWithImage>(
    private var dialog: MaterialDialog,
    private var items: List<IT>,
    disabledItems: IntArray?,
    private var waitForPositiveButton: Boolean,
    private var selection: ListItemListener<IT>
) : RecyclerView.Adapter<ListItemViewHolder>(), DialogAdapter<IT, ListItemListener<IT>> {

    private var disabledIndices: IntArray = disabledItems ?: IntArray(0)

    fun itemClicked(index: Int) {
        if (waitForPositiveButton && dialog.hasActionButton(WhichButton.POSITIVE)) {
            // Wait for positive action button, mark clicked item as activated so that we can call the
            // selection listener when the button is pressed.
            val lastActivated = dialog.config[KEY_ACTIVATED_INDEX] as? Int
            dialog.config[KEY_ACTIVATED_INDEX] = index
            if (lastActivated != null) {
                notifyItemChanged(lastActivated)
            }
            notifyItemChanged(index)
        } else {
            // Don't wait for action buttons, call listener and dismiss if auto dismiss is applicable
            this.selection?.invoke(dialog, index, this.items[index])
            if (dialog.autoDismissEnabled && !dialog.hasActionButtons()) {
                dialog.dismiss()
            }
        }
    }

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ListItemViewHolder {
        val listItemView: View = parent.inflate(dialog.windowContext, R.layout.menu_item_sheet)
        val viewHolder = ListItemViewHolder(
            itemView = listItemView,
            adapter = this
        )
        viewHolder.titleView.maybeSetTextColor(dialog.windowContext, R.attr.md_color_content)
        return viewHolder
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(
        holder: ListItemViewHolder,
        position: Int
    ) {
        holder.itemView.isEnabled = !disabledIndices.contains(position)
        val currentItem = items[position]

        holder.titleView.text = currentItem.title
        holder.itemView.background = dialog.getItemSelector()
        currentItem.populateIcon(holder.iconView)

        val activatedIndex = dialog.config[KEY_ACTIVATED_INDEX] as? Int
        holder.itemView.isActivated = activatedIndex != null && activatedIndex == position

        if (dialog.bodyFont != null) {
            holder.titleView.typeface = dialog.bodyFont
        }
    }

    override fun positiveButtonClicked() {
        val activatedIndex = dialog.config[KEY_ACTIVATED_INDEX] as? Int
        if (activatedIndex != null) {
            selection?.invoke(dialog, activatedIndex, items[activatedIndex])
            dialog.config.remove(KEY_ACTIVATED_INDEX)
        }
    }

    override fun replaceItems(
        items: List<IT>,
        listener: ListItemListener<IT>
    ) {
        this.items = items
        if (listener != null) {
            this.selection = listener
        }
        this.notifyDataSetChanged()
    }

    override fun disableItems(indices: IntArray) {
        this.disabledIndices = indices
        notifyDataSetChanged()
    }

    override fun checkItems(indices: IntArray) = Unit

    override fun uncheckItems(indices: IntArray) = Unit

    override fun toggleItems(indices: IntArray) = Unit

    override fun checkAllItems() = Unit

    override fun uncheckAllItems() = Unit

    override fun toggleAllChecked() = Unit

    override fun isItemChecked(index: Int) = false
}
