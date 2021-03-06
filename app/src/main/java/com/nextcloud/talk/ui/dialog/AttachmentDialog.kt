/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2021 Marcel Hibbe <marcel.hibbe@nextcloud.com>
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
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatTextView
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.Unbinder
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.components.filebrowser.controllers.BrowserController
import com.nextcloud.talk.controllers.ChatController


class AttachmentDialog(val activity: Activity, var chatController :ChatController) : BottomSheetDialog(activity) {

    @BindView(R.id.txt_attach_file_from_local)
    @JvmField
    var attachFromLocal: AppCompatTextView? = null

    @BindView(R.id.txt_attach_file_from_cloud)
    @JvmField
    var attachFromCloud: AppCompatTextView? = null

    private var unbinder: Unbinder? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val view = layoutInflater.inflate(R.layout.dialog_attachment, null)
        setContentView(view)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        unbinder = ButterKnife.bind(this, view)

        attachFromLocal?.setOnClickListener {
            chatController.sendSelectLocalFileIntent()
            dismiss()
        }
        attachFromCloud?.setOnClickListener {
            chatController.showBrowserScreen(BrowserController.BrowserType.DAV_BROWSER)
            dismiss()
        }
    }
}
