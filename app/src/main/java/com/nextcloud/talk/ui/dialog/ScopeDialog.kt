/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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

package com.nextcloud.talk.ui.dialog

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.controllers.ProfileController
import com.nextcloud.talk.models.json.userprofile.Scope

class ScopeDialog(
    con: Context,
    private val userInfoAdapter: ProfileController.UserInfoAdapter,
    private val field: ProfileController.Field,
    private val position: Int
) :
    BottomSheetDialog(con) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.dialog_scope, null)
        setContentView(view)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        if (field == ProfileController.Field.DISPLAYNAME || field == ProfileController.Field.EMAIL) {
            findViewById<LinearLayout>(R.id.scope_private)?.visibility = View.GONE
        }

        findViewById<LinearLayout>(R.id.scope_private)?.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.PRIVATE)
            dismiss()
        }

        findViewById<LinearLayout>(R.id.scope_local)?.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.LOCAL)
            dismiss()
        }

        findViewById<LinearLayout>(R.id.scope_federated)?.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.FEDERATED)
            dismiss()
        }

        findViewById<LinearLayout>(R.id.scope_published)?.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.PUBLISHED)
            dismiss()
        }
    }
}
