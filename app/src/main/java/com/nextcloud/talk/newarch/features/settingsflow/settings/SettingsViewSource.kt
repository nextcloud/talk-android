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
package com.nextcloud.talk.newarch.features.settingsflow.settings

import androidx.lifecycle.LiveData
import com.nextcloud.talk.newarch.local.models.User
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.extensions.MainSource

class SettingsViewSource<T : User>(private val data: LiveData<List<T>>, loadingIndicatorsEnabled: Boolean = true, errorIndicatorEnabled: Boolean = false, emptyIndicatorEnabled: Boolean = false) : MainSource<T>(loadingIndicatorsEnabled, errorIndicatorEnabled, emptyIndicatorEnabled) {
    private var currentPage: Page? = null
    override fun onPageOpened(page: Page, dependencies: List<Element<*>>) {
        super.onPageOpened(page, dependencies)
        if (page.previous() == null) {
            currentPage = page
            postResult(page, data)
        }
    }

    override fun areItemsTheSame(first: T, second: T): Boolean {
        return first.id == second.id
    }

    override fun getElementType(data: T): Int {
        return SettingsElementType.USER.ordinal
    }
}