/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */

package com.nextcloud.talk.newarch.data.presenters

import android.content.Context
import android.view.View
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.extensions.EmptyPresenter

class AdvancedEmptyPresenter(context: Context, layout: Int, private val onViewClick: (() -> Unit)? = null, private val bind: ((View) -> Unit)? = null) : EmptyPresenter(context, layout) {
    override fun onBind(page: Page, holder: Holder, element: Element<Void>, payloads: List<Any>) {
        super.onBind(page, holder, element, payloads)
        holder.itemView.setOnClickListener {
            onViewClick?.invoke()
        }
        bind?.invoke(holder.itemView)
    }
}