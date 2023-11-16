/*
 * Nextcloud Talk application
 *
 * @author Julius Linus
 * Copyright (C) 2023 Julius Linus <julius.linus@nextcloud.com>
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

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogFileAttachmentPreviewBinding
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.permissions.PlatformPermissionUtil
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class FileAttachmentPreviewFragment(
    filenames: String,
    filesToUpload: MutableList<String>,
    functionToCall: (files: MutableList<String>, caption: String) -> Unit
) : DialogFragment() {
    private val files = filenames
    private val filesList = filesToUpload
    private val uploadFiles = functionToCall
    lateinit var binding: DialogFileAttachmentPreviewBinding

    @Inject
    lateinit var permissionUtil: PlatformPermissionUtil

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogFileAttachmentPreviewBinding.inflate(LayoutInflater.from(context))
        return MaterialAlertDialogBuilder(requireContext()).setView(binding.root).create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        setUpViews()
        setUpListeners()
        return inflater.inflate(R.layout.dialog_file_attachment_preview, container, false)
    }

    private fun setUpViews() {
        binding.dialogFileAttachmentPreviewFilenames.text = files
        viewThemeUtils.material.colorMaterialButtonPrimaryBorderless(binding.buttonClose)
        viewThemeUtils.material.colorMaterialButtonPrimaryBorderless(binding.buttonSend)
        viewThemeUtils.platform.colorViewBackground(binding.root)
        viewThemeUtils.material.colorTextInputLayout(binding.dialogFileAttachmentPreviewLayout)
    }

    private fun setUpListeners() {
        binding.buttonClose.setOnClickListener {
            dismiss()
        }

        binding.buttonSend.setOnClickListener {
            if (permissionUtil.isFilesPermissionGranted()) {
                val caption: String = binding.dialogFileAttachmentPreviewCaption.text.toString()
                uploadFiles(filesList, caption)
            } else {
                UploadAndShareFilesWorker.requestStoragePermission(requireActivity())
            }
            dismiss()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(
            filenames: String,
            filesToUpload: MutableList<String>,
            functionToCall: (files: MutableList<String>, caption: String) -> Unit
        ) =
            FileAttachmentPreviewFragment(filenames, filesToUpload, functionToCall)
        val TAG: String = FilterConversationFragment::class.java.simpleName
    }
}
