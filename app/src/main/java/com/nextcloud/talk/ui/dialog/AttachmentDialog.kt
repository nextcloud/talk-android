/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
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
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController
import com.nextcloud.talk.controllers.ChatController
import com.nextcloud.talk.databinding.DialogAttachmentBinding
import com.nextcloud.talk.models.database.CapabilitiesUtil

class AttachmentDialog(val activity: Activity, var chatController: ChatController) : BottomSheetDialog(activity) {
    lateinit var binding: DialogAttachmentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DialogAttachmentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        var serverName = CapabilitiesUtil.getServerName(chatController.conversationUser)
        binding.shareFileFromCloudText.text = chatController.resources?.let {
            if (serverName.isNullOrEmpty()) {
                serverName = it.getString(R.string.nc_server_product_name)
            }
            String.format(it.getString(R.string.nc_upload_from_cloud), serverName)
        }

        if (!CapabilitiesUtil.hasSpreedFeatureCapability(
                chatController.conversationUser,
                "geo-location-sharing"
            )
        ) {
            binding.shareLocationOption.visibility = View.GONE
        }

        binding.shareLocationText.setOnClickListener {
            chatController.showShareLocationScreen()
            dismiss()
        }

        binding.shareFileFromLocalText.setOnClickListener {
            chatController.sendSelectLocalFileIntent()
            dismiss()
        }

        binding.shareFileFromCloudText.setOnClickListener {
            chatController.showBrowserScreen(BrowserController.BrowserType.DAV_BROWSER)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }
}
