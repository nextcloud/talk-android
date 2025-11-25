/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe (dev@mhibbe.de)
 * SPDX-FileCopyrightText: 2023 Fariba Khandani <khandani@winworker.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import autodagger.AutoInjector
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import com.nextcloud.talk.jobs.SaveFileToStorageWorker
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import java.util.concurrent.ExecutionException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class SaveToStorageDialogFragment : DialogFragment() {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils
    lateinit var fileName: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sharedApplication!!.componentApplication.inject(this)
        fileName = arguments?.getString(KEY_FILE_NAME)!!
    }
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogText = StringBuilder()
        dialogText.append(resources.getString(R.string.nc_dialog_save_to_storage_content))
        dialogText.append("\n")
        dialogText.append("\n")
        dialogText.append(resources.getString(R.string.nc_dialog_save_to_storage_continue))

        val dialogBuilder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.nc_dialog_save_to_storage_title)
            .setMessage(dialogText)
            .setPositiveButton(R.string.nc_dialog_save_to_storage_yes) { _: DialogInterface?, _: Int ->
                saveImageToStorage(fileName)
            }
            .setNegativeButton(R.string.nc_dialog_save_to_storage_no) { _: DialogInterface?, _: Int ->
            }
        viewThemeUtils.dialog.colorMaterialAlertDialogBackground(
            requireContext(),
            dialogBuilder
        )
        val dialog = dialogBuilder.show()
        viewThemeUtils.platform.colorTextButtons(
            dialog.getButton(AlertDialog.BUTTON_POSITIVE),
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        )

        return dialog
    }

    @SuppressLint("LongLogTag")
    private fun saveImageToStorage(fileName: String) {
        val sourceFilePath = requireContext().cacheDir.path
        val workerTag = SAVE_TO_STORAGE_WORKER_PREFIX + fileName

        val workers = WorkManager.getInstance(requireContext()).getWorkInfosByTag(workerTag)
        try {
            for (workInfo in workers.get()) {
                if (workInfo.state == WorkInfo.State.RUNNING || workInfo.state == WorkInfo.State.ENQUEUED) {
                    return
                }
            }
        } catch (e: ExecutionException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error when checking if worker already exists", e)
        }

        val data: Data = Data.Builder()
            .putString(SaveFileToStorageWorker.KEY_FILE_NAME, fileName)
            .putString(SaveFileToStorageWorker.KEY_SOURCE_FILE_PATH, "$sourceFilePath/$fileName")
            .build()

        val saveWorker: OneTimeWorkRequest = OneTimeWorkRequest.Builder(SaveFileToStorageWorker::class.java)
            .setInputData(data)
            .addTag(workerTag)
            .build()

        WorkManager.getInstance().enqueue(saveWorker)
    }

    companion object {
        val TAG = SaveToStorageDialogFragment::class.java.simpleName
        private const val KEY_FILE_NAME = "keyFileName"
        private const val SAVE_TO_STORAGE_WORKER_PREFIX = "saveToStorage_"

        fun newInstance(fileName: String): SaveToStorageDialogFragment {
            val args = Bundle()
            args.putString(KEY_FILE_NAME, fileName)
            val fragment = SaveToStorageDialogFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
