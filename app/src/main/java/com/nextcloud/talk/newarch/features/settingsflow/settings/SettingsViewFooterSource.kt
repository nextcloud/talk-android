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

import android.content.Context
import com.nextcloud.talk.R
import com.nextcloud.talk.newarch.local.models.User
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.extensions.FooterSource

class SettingsViewFooterSource(private val context: Context) : FooterSource<User, String>() {
    private var lastAnchor: User? = null

    override fun areItemsTheSame(first: Data<User, String>, second: Data<User, String>): Boolean {
        return first == second
    }

    override fun getElementType(data: Data<User, String>): Int {
        return SettingsElementType.NEW_USER.ordinal
    }

    override fun dependsOn(source: Source<*>): Boolean {
        return source is SettingsViewSource
    }

    override fun computeFooters(page: Page, list: List<User>): List<Data<User, String>> {
        val results = arrayListOf<Data<User, String>>()
        lastAnchor = if (list.isNotEmpty()) {
            val user = list.takeLast(1)[0]
            results.add(Data(user, context.resources.getString(R.string.nc_settings_new_account)))
            user
        } else {
            null
        }

        return results
    }

}
