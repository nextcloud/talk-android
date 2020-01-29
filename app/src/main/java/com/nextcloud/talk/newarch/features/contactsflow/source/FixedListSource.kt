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

package com.nextcloud.talk.newarch.features.contactsflow.source

import com.nextcloud.talk.newarch.features.contactsflow.ContactsViewSource
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.extensions.ListSource

class FixedListSource(list: List<Any>, elementType: Int) : ListSource<Any>(list, elementType) {
    override fun areContentsTheSame(first: Any, second: Any): Boolean {
        return true
    }

    override fun <E : Any> areItemsTheSame(own: Any, dependency: Source<E>, other: E?): Boolean {
        return true
    }

    override fun dependsOn(source: Source<*>): Boolean {
        return source is ContactsViewSource
    }


}