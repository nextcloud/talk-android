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

package com.nextcloud.talk.newarch.features.contactsflow.contacts

import androidx.lifecycle.LiveData
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.newarch.features.contactsflow.ParticipantElement
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.extensions.MainSource

class ContactsViewSource<T : ParticipantElement>(private val data: LiveData<List<T>>, loadingIndicatorsEnabled: Boolean = true, errorIndicatorEnabled: Boolean = true, emptyIndicatorEnabled: Boolean = true) : MainSource<T>(loadingIndicatorsEnabled, errorIndicatorEnabled, emptyIndicatorEnabled) {
    private var currentPage: Page? = null
    override fun onPageOpened(page: Page, dependencies: List<Element<*>>) {
        super.onPageOpened(page, dependencies)
        if (page.previous() == null) {
            currentPage = page
            postResult(page, data)
        }
    }

    fun postError(exception: Exception) {
        currentPage?.let { page ->
            postResult(page, exception)
        }
    }

    override fun getElementType(data: T): Int {
        return data.elementType
    }

    override fun dependsOn(source: Source<*>) = false

    override fun areContentsTheSame(first: T, second: T): Boolean {
        return first == second
    }

    override fun areItemsTheSame(first: T, second: T): Boolean {
        if (first.elementType != second.elementType) {
            return false
        }

        if (first.data is Participant && second.data is Participant) {
            return first.data.userId == second.data.userId
        }

        return first.data == second.data
    }
}