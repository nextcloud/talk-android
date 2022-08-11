/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * @author Andy Scherzinger
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.controllers.ProfileController
import com.nextcloud.talk.databinding.DialogScopeBinding
import com.nextcloud.talk.models.json.userprofile.Scope
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class ScopeDialog(
    con: Context,
    private val userInfoAdapter: ProfileController.UserInfoAdapter,
    private val field: ProfileController.Field,
    private val position: Int
) : BottomSheetDialog(con) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var dialogScopeBinding: DialogScopeBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        dialogScopeBinding = DialogScopeBinding.inflate(layoutInflater)
        setContentView(dialogScopeBinding.root)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.themeDialog(dialogScopeBinding.root)

        if (field == ProfileController.Field.DISPLAYNAME || field == ProfileController.Field.EMAIL) {
            dialogScopeBinding.scopePrivate.visibility = View.GONE
        }

        dialogScopeBinding.scopePrivate.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.PRIVATE)
            dismiss()
        }

        dialogScopeBinding.scopeLocal.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.LOCAL)
            dismiss()
        }

        dialogScopeBinding.scopeFederated.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.FEDERATED)
            dismiss()
        }

        dialogScopeBinding.scopePublished.setOnClickListener {
            userInfoAdapter.updateScope(position, Scope.PUBLISHED)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }
}
