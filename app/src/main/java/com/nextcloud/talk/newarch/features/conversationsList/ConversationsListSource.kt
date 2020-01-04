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

package com.nextcloud.talk.newarch.features.conversationsList

import androidx.lifecycle.LiveData
import com.nextcloud.talk.models.json.conversations.Conversation
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.extensions.MainSource

class ConversationsListSource<T: Conversation>(private val data: LiveData<List<T>>, private val elementType: Int = 0, loadingIndicatorsEnabled: Boolean = true, errorIndicatorEnabled: Boolean = true, emptyIndicatorEnabled: Boolean = true) : MainSource<T>(loadingIndicatorsEnabled, errorIndicatorEnabled, emptyIndicatorEnabled) {

    override fun onPageOpened(page: Page, dependencies: List<Element<*>>) {
        super.onPageOpened(page, dependencies)
        if (page.previous() == null) {
            postResult(page, data)
        }
    }

    override fun dependsOn(source: Source<*>): Boolean {
        return false
    }

    override fun areItemsTheSame(first: T, second: T): Boolean {
        return first.databaseId == second.databaseId
    }

}