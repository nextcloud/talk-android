/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogMoreCallActionsBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.viewmodels.CallRecordingViewModel
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class MoreCallActionsDialog(private val callActivity: CallActivity) : BottomSheetDialog(callActivity) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var binding: DialogMoreCallActionsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        binding = DialogMoreCallActionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.platform.themeDialogDark(binding.root)

        initItemsVisibility()
        initClickListeners()
        initObservers()
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    private fun initItemsVisibility() {
        if (callActivity.isAllowedToStartOrStopRecording) {
            binding.recordCall.visibility = View.VISIBLE
        } else {
            binding.recordCall.visibility = View.GONE
        }

        if (callActivity.isAllowedToRaiseHand) {
            binding.raiseHand.visibility = View.VISIBLE
        } else {
            binding.raiseHand.visibility = View.GONE
        }
    }

    private fun initClickListeners() {
        binding.recordCall.setOnClickListener {
            callActivity.callRecordingViewModel.clickRecordButton()
        }

        binding.raiseHand.setOnClickListener {
            // TODO: save raised hand state & toggle...
            callActivity.clickHand(true)
        }
    }

    private fun initObservers() {
        callActivity.callRecordingViewModel.viewState.observe(this) { state ->
            when (state) {
                is CallRecordingViewModel.RecordingStartedState -> {
                    binding.recordCallText.text = context.getText(R.string.record_stop_description)
                    binding.recordCallIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.record_stop)
                    )
                    dismiss()
                }
                is CallRecordingViewModel.RecordingStoppedState -> {
                    binding.recordCallText.text = context.getText(R.string.record_start_description)
                    binding.recordCallIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.record_start)
                    )
                    dismiss()
                }
                is CallRecordingViewModel.RecordingStartLoadingState -> {
                    binding.recordCallText.text = context.getText(R.string.record_start_loading)
                }
                is CallRecordingViewModel.RecordingStopLoadingState -> {
                    binding.recordCallText.text = context.getText(R.string.record_stop_loading)
                }
                is CallRecordingViewModel.RecordingConfirmStopState -> {
                    binding.recordCallText.text = context.getText(R.string.record_stop_description)
                }
                else -> {
                    Log.e(TAG, "unknown viewState for callRecordingViewModel")
                }
            }
        }
    }

    companion object {
        private const val TAG = "MoreCallActionsDialog"
    }
}
