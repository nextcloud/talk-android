/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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

package com.nextcloud.talk.shareditems.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.SharedItemGridBinding
import com.nextcloud.talk.databinding.SharedItemListBinding
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.shareditems.model.SharedItem

class SharedItemsAdapter(
    private val showGrid: Boolean,
    private val userEntity: UserEntity
) : RecyclerView.Adapter<SharedItemsViewHolder>() {

    var items: List<SharedItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharedItemsViewHolder {

        return if (showGrid) {
            SharedItemsGridViewHolder(
                SharedItemGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                userEntity
            )
        } else {
            SharedItemsListViewHolder(
                SharedItemListBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                userEntity
            )
        }
    }

    override fun onBindViewHolder(holder: SharedItemsViewHolder, position: Int) {
        holder.onBind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
