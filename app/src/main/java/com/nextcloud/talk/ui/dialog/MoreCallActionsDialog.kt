/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.dialog

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogMoreCallActionsBinding
import com.nextcloud.talk.raisehand.viewmodel.RaiseHandViewModel
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.viewmodels.CallRecordingViewModel
import com.vanniktech.emoji.EmojiTextView
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
        initEmojiBar()
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
        if (CapabilitiesUtil.isCallReactionsSupported(callActivity.conversationUser)) {
            binding.callEmojiBar.visibility = View.VISIBLE
        } else {
            binding.callEmojiBar.visibility = View.GONE
        }

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
            callActivity.callRecordingViewModel?.clickRecordButton()
        }

        binding.raiseHand.setOnClickListener {
            callActivity.clickRaiseOrLowerHandButton()
        }
    }

    private fun initEmojiBar() {
        if (CapabilitiesUtil.isCallReactionsSupported(callActivity.conversationUser)) {
            binding.advancedCallOptionsTitle.visibility = View.GONE

            val capabilities = callActivity.conversationUser?.capabilities
            val availableReactions: ArrayList<*> =
                capabilities?.spreedCapability?.config!!["call"]!!["supported-reactions"] as ArrayList<*>

            val param = LinearLayout.LayoutParams(
                DisplayUtils.convertDpToPixel(EMOJI_WIDTH.toFloat(), callActivity).toInt(),
                LinearLayout.LayoutParams.MATCH_PARENT
            )

            availableReactions.forEach {
                val emojiView = EmojiTextView(context)
                emojiView.text = it.toString()
                emojiView.textSize = TEXT_SIZE
                emojiView.layoutParams = param

                emojiView.setOnClickListener { view ->
                    callActivity.sendReaction((view as EmojiTextView).text.toString())
                    dismiss()
                }
                binding.callEmojiBar.addView(emojiView)
            }
        } else {
            binding.callEmojiBar.visibility = View.GONE
        }
    }

    private fun initObservers() {
        callActivity.callRecordingViewModel?.viewState?.observe(this) { state ->
            when (state) {
                is CallRecordingViewModel.RecordingStoppedState,
                is CallRecordingViewModel.RecordingErrorState -> {
                    binding.recordCallText.text = context.getText(R.string.record_start_description)
                    binding.recordCallIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.record_start)
                    )
                    dismiss()
                }

                is CallRecordingViewModel.RecordingStartingState -> {
                    binding.recordCallText.text = context.getText(R.string.record_cancel_start)
                    binding.recordCallIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.record_stop)
                    )
                }

                is CallRecordingViewModel.RecordingStartedState -> {
                    binding.recordCallText.text = context.getText(R.string.record_stop_description)
                    binding.recordCallIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.record_stop)
                    )
                    dismiss()
                }

                is CallRecordingViewModel.RecordingStoppingState -> {
                    binding.recordCallText.text = context.getText(R.string.record_stopping)
                }

                is CallRecordingViewModel.RecordingConfirmStopState -> {
                    binding.recordCallText.text = context.getText(R.string.record_stop_description)
                }

                else -> {
                    Log.e(TAG, "unknown viewState for callRecordingViewModel")
                }
            }
        }

        callActivity.raiseHandViewModel?.viewState?.observe(this) { state ->
            when (state) {
                is RaiseHandViewModel.RaisedHandState -> {
                    binding.raiseHandText.text = context.getText(R.string.lower_hand)
                    binding.raiseHandIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_baseline_do_not_touch_24)
                    )
                    dismiss()
                }

                is RaiseHandViewModel.LoweredHandState -> {
                    binding.raiseHandText.text = context.getText(R.string.raise_hand)
                    binding.raiseHandIcon.setImageDrawable(
                        ContextCompat.getDrawable(context, R.drawable.ic_hand_back_left)
                    )
                    dismiss()
                }

                else -> {}
            }
        }
    }

    companion object {
        private const val TAG = "MoreCallActionsDialog"
        private const val TEXT_SIZE = 20f
        private const val EMOJI_WIDTH = 40
    }
}
