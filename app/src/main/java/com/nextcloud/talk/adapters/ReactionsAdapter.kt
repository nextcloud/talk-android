/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.ReactionItemBinding
import com.nextcloud.talk.models.database.UserEntity

class ReactionsAdapter(
    private val clickListener: ReactionItemClickListener,
    private val userEntity: UserEntity?
) : RecyclerView.Adapter<ReactionsViewHolder>() {
    internal var list: MutableList<ReactionItem> = ArrayList<ReactionItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReactionsViewHolder {
        val itemBinding = ReactionItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ReactionsViewHolder(itemBinding, userEntity?.baseUrl)
    }

    override fun onBindViewHolder(holder: ReactionsViewHolder, position: Int) {
        holder.bind(list[position], clickListener)
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
