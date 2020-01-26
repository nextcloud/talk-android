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

package com.nextcloud.talk.newarch.features.contactsflow

import android.content.Context
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.participants.Participant
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.extensions.FooterSource

class ContactsViewFooterSource(private val context: Context, private val elementType: Int) : FooterSource<Participant, String>() {
    private var lastAnchor: Participant? = null

    override fun dependsOn(source: Source<*>): Boolean {
        return source is ContactsViewSource
    }

    override fun areItemsTheSame(first: Data<Participant, String>, second: Data<Participant, String>): Boolean {
        return first == second
    }

    override fun getElementType(data: Data<Participant, String>) = elementType

    override fun computeFooters(page: Page, list: List<Participant>): List<Data<Participant, String>> {
        lastAnchor = null
        val results = arrayListOf<Data<Participant, String>>()
        lastAnchor = if (list.isNotEmpty()) {
            val participant = list.takeLast(1)[0]

            if (lastAnchor == null || lastAnchor != participant) {
                results.add(Data(participant, context.getString(R.string.nc_search_for_more)))
            }

            participant
        } else {
            null
        }

        return results
    }
}