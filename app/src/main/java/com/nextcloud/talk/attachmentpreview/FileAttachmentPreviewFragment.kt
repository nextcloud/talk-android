/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Julius Linus <julius.linus@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class FileAttachmentPreviewFragment : DialogFragment() {
    private lateinit var filesList: ArrayList<String>
    private var conversationName: String = ""
    private var uploadFiles: (files: MutableList<String>, caption: String, compressImages: Boolean) -> Unit =
        { _, _, _ -> }
    private var composeView: ComposeView? = null

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    private val viewModel: FileAttachmentPreviewViewModel by lazy {
        ViewModelProvider(this, viewModelFactory)[FileAttachmentPreviewViewModel::class.java]
    }

    fun setListener(uploadFiles: (files: MutableList<String>, caption: String, compressImages: Boolean) -> Unit) {
        this.uploadFiles = uploadFiles
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        arguments?.let {
            filesList = it.getStringArrayList(FILES_TO_UPLOAD_ARG)!!
            conversationName = it.getString(CONVERSATION_NAME_ARG, "")
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
            setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
            setBackgroundDrawableResource(android.R.color.transparent)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            clearFlags(WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM)
            // Dialog windows dim whatever is behind them by default; at fullscreen size that
            // scrim covers the whole window, including the status bar area, making it look dark
            // regardless of the Compose content's own color.
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            // targetSdk 36 enforces edge-to-edge; Window.setStatusBarColor() is a no-op there.
            // Instead, let the dialog draw behind the status bar so the Compose Surface's own
            // background shows through it, and inset the content with statusBarsPadding().
            WindowCompat.setDecorFitsSystemWindows(this, false)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT

            val surfaceColor = viewThemeUtils.getColorScheme(requireActivity()).surface
            val isLightSurface = surfaceColor.luminance() > LIGHT_LUMINANCE_THRESHOLD
            WindowInsetsControllerCompat(this, decorView).apply {
                isAppearanceLightStatusBars = isLightSurface
                isAppearanceLightNavigationBars = isLightSurface
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        // No-ops if the dialog is being recreated (e.g. after rotation) and the ViewModel
        // already holds an in-progress edit of the file list.
        viewModel.setInitialFiles(filesList)

        composeView?.apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(requireActivity())) {
                    FileAttachmentPreviewContent(
                        viewModel = viewModel,
                        conversationName = conversationName,
                        initialCompressImages = appPreferences.compressUploadImages,
                        onDismiss = { dismiss() },
                        onSend = { files, caption, compressImages ->
                            uploadFiles(files.toMutableList(), caption, compressImages)
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

        private const val LIGHT_LUMINANCE_THRESHOLD = 0.5f
        private const val FILES_TO_UPLOAD_ARG = "FILES_TO_UPLOAD_ARG"
        private const val CONVERSATION_NAME_ARG = "CONVERSATION_NAME_ARG"

        @JvmStatic
        fun newInstance(filesToUpload: MutableList<String>, conversationName: String): FileAttachmentPreviewFragment {
            val fileAttachmentFragment = FileAttachmentPreviewFragment()
            val args = Bundle()
            args.putStringArrayList(FILES_TO_UPLOAD_ARG, ArrayList(filesToUpload))
            args.putString(CONVERSATION_NAME_ARG, conversationName)
            fileAttachmentFragment.arguments = args
            return fileAttachmentFragment
        }

        val TAG: String = FileAttachmentPreviewFragment::class.java.simpleName
    }
}
