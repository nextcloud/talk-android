/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.databinding.DialogAttachmentBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class AttachmentDialog(val activity: Activity, var chatActivity: ChatActivity) : BottomSheetDialog(activity) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var dialogAttachmentBinding: DialogAttachmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        dialogAttachmentBinding = DialogAttachmentBinding.inflate(layoutInflater)
        setContentView(dialogAttachmentBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.platform.themeDialog(dialogAttachmentBinding.root)
        initItemsStrings()
        initItemsVisibility()
        initItemsClickListeners()
    }

    private fun initItemsStrings() {
        var serverName = CapabilitiesUtilNew.getServerName(chatActivity.conversationUser)
        dialogAttachmentBinding.txtAttachFileFromCloud.text = chatActivity.resources?.let {
            if (serverName.isNullOrEmpty()) {
                serverName = it.getString(R.string.nc_server_product_name)
            }
            String.format(it.getString(R.string.nc_upload_from_cloud), serverName)
        }
    }

    private fun initItemsVisibility() {
        if (!CapabilitiesUtilNew.hasSpreedFeatureCapability(
                chatActivity.conversationUser,
                "geo-location-sharing"
            )
        ) {
            dialogAttachmentBinding.menuShareLocation.visibility = View.GONE
        }

        if (!CapabilitiesUtilNew.hasSpreedFeatureCapability(chatActivity.conversationUser, "talk-polls") ||
            chatActivity.isOneToOneConversation()
        ) {
            dialogAttachmentBinding.menuAttachPoll.visibility = View.GONE
        }

        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            dialogAttachmentBinding.menuAttachVideoFromCam.visibility = View.GONE
        }
    }

    private fun initItemsClickListeners() {
        dialogAttachmentBinding.menuShareLocation.setOnClickListener {
            chatActivity.showShareLocationScreen()
            dismiss()
        }

        dialogAttachmentBinding.menuAttachFileFromLocal.setOnClickListener {
            chatActivity.sendSelectLocalFileIntent()
            dismiss()
        }

        dialogAttachmentBinding.menuAttachPictureFromCam.setOnClickListener {
            chatActivity.sendPictureFromCamIntent()
            dismiss()
        }

        dialogAttachmentBinding.menuAttachVideoFromCam.setOnClickListener {
            chatActivity.sendVideoFromCamIntent()
            dismiss()
        }

        dialogAttachmentBinding.menuAttachPoll.setOnClickListener {
            chatActivity.createPoll()
            dismiss()
        }

        dialogAttachmentBinding.menuAttachFileFromCloud.setOnClickListener {
            chatActivity.showBrowserScreen()
            dismiss()
        }

        dialogAttachmentBinding.menuAttachContact.setOnClickListener {
            chatActivity.sendChooseContactIntent()
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
