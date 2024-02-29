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
class FileAttachmentPreviewFragment : DialogFragment() {
    private lateinit var files: String
    private lateinit var filesList: ArrayList<String>
    private var uploadFiles: (files: MutableList<String>, caption: String) -> Unit = { _, _ -> }
    lateinit var binding: DialogFileAttachmentPreviewBinding

    @Inject
    lateinit var permissionUtil: PlatformPermissionUtil

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    fun setListener(uploadFiles: (files: MutableList<String>, caption: String) -> Unit) {
        this.uploadFiles = uploadFiles
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {

        arguments?.let {
            files = it.getString(FILE_NAMES_ARG, "")
            filesList = it.getStringArrayList(FILES_TO_UPLOAD_ARG)!!
        }

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

        private const val FILE_NAMES_ARG = "FILE_NAMES_ARG"
        private const val FILES_TO_UPLOAD_ARG = "FILES_TO_UPLOAD_ARG"

        @JvmStatic
        fun newInstance(
            filenames: String,
            filesToUpload: MutableList<String>,
        ): FileAttachmentPreviewFragment {
            val fileAttachmentFragment = FileAttachmentPreviewFragment()
            val args = Bundle()
            args.putString(FILE_NAMES_ARG, filenames)
            args.putStringArrayList(FILES_TO_UPLOAD_ARG, ArrayList(filesToUpload))
            fileAttachmentFragment.arguments = args
            return fileAttachmentFragment
        }

        val TAG: String = FilterConversationFragment::class.java.simpleName
    }
}
