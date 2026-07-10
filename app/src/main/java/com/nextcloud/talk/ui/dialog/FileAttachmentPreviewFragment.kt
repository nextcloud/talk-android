/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Julius Linus <julius.linus@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class FileAttachmentPreviewFragment : DialogFragment() {
    private lateinit var filesList: ArrayList<String>
    private var uploadFiles: (files: MutableList<String>, caption: String, compressImages: Boolean) -> Unit =
        { _, _, _ -> }
    private var composeView: ComposeView? = null

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var appPreferences: AppPreferences

    fun setListener(uploadFiles: (files: MutableList<String>, caption: String, compressImages: Boolean) -> Unit) {
        this.uploadFiles = uploadFiles
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        arguments?.let {
            filesList = it.getStringArrayList(FILES_TO_UPLOAD_ARG)!!
        }

        composeView = ComposeView(requireContext())
        return MaterialAlertDialogBuilder(requireContext()).setView(composeView).create()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        composeView

    @Suppress("DEPRECATION")
    override fun onStart() {
        super.onStart()
        // Dialog.show() re-applies FLAG_ALT_FOCUSABLE_IM from the theme after onCreateDialog()
        // runs, so clearing it must happen once the dialog window is actually up. Without this,
        // the system never routes the keyboard to this window, even while it holds input focus.
        dialog?.window?.apply {
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        composeView?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(requireActivity())) {
                    FileAttachmentPreviewContent(
                        files = filesList,
                        initialCompressImages = appPreferences.compressUploadImages,
                        onDismiss = { dismiss() },
                        onSend = { caption, compressImages ->
                            uploadFiles(filesList, caption, compressImages)
                            dismiss()
                        }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        composeView = null
    }

    companion object {

        private const val FILES_TO_UPLOAD_ARG = "FILES_TO_UPLOAD_ARG"

        @JvmStatic
        fun newInstance(filesToUpload: MutableList<String>): FileAttachmentPreviewFragment {
            val fileAttachmentFragment = FileAttachmentPreviewFragment()
            val args = Bundle()
            args.putStringArrayList(FILES_TO_UPLOAD_ARG, ArrayList(filesToUpload))
            fileAttachmentFragment.arguments = args
            return fileAttachmentFragment
        }

        val TAG: String = FileAttachmentPreviewFragment::class.java.simpleName
    }
}
