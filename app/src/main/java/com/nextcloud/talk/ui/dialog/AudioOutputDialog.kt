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
import autodagger.AutoInjector
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.databinding.DialogAudioOutputBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.webrtc.WebRtcAudioManager
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class AudioOutputDialog(val callActivity: CallActivity) : BottomSheetDialog(callActivity) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    private lateinit var dialogAudioOutputBinding: DialogAudioOutputBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)

        dialogAudioOutputBinding = DialogAudioOutputBinding.inflate(layoutInflater)
        setContentView(dialogAudioOutputBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        viewThemeUtils.platform.themeDialogDark(dialogAudioOutputBinding.root)
        updateOutputDeviceList()
        initClickListeners()
    }

    fun updateOutputDeviceList() {
        if (callActivity.audioManager?.audioDevices?.contains(WebRtcAudioManager.AudioDevice.BLUETOOTH) == false) {
            dialogAudioOutputBinding.audioOutputBluetooth.visibility = View.GONE
        } else {
            dialogAudioOutputBinding.audioOutputBluetooth.visibility = View.VISIBLE
        }

        if (callActivity.audioManager?.audioDevices?.contains(WebRtcAudioManager.AudioDevice.EARPIECE) == false) {
            dialogAudioOutputBinding.audioOutputEarspeaker.visibility = View.GONE
        } else {
            dialogAudioOutputBinding.audioOutputEarspeaker.visibility = View.VISIBLE
        }

        if (callActivity.audioManager?.audioDevices?.contains(WebRtcAudioManager.AudioDevice.SPEAKER_PHONE) == false) {
            dialogAudioOutputBinding.audioOutputSpeaker.visibility = View.GONE
        } else {
            dialogAudioOutputBinding.audioOutputSpeaker.visibility = View.VISIBLE
        }

        if (callActivity.audioManager?.currentAudioDevice?.equals(
                WebRtcAudioManager.AudioDevice.WIRED_HEADSET
            ) == true
        ) {
            dialogAudioOutputBinding.audioOutputEarspeaker.visibility = View.GONE
            dialogAudioOutputBinding.audioOutputSpeaker.visibility = View.GONE
            dialogAudioOutputBinding.audioOutputWiredHeadset.visibility = View.VISIBLE
        } else {
            dialogAudioOutputBinding.audioOutputWiredHeadset.visibility = View.GONE
        }

        highlightActiveOutputChannel()
    }

    private fun highlightActiveOutputChannel() {
        when (callActivity.audioManager?.currentAudioDevice) {
            WebRtcAudioManager.AudioDevice.BLUETOOTH -> {
                viewThemeUtils.platform.colorImageView(
                    dialogAudioOutputBinding.audioOutputBluetoothIcon,
                    ColorRole.PRIMARY
                )
                viewThemeUtils.platform
                    .colorPrimaryTextViewElementDarkMode(dialogAudioOutputBinding.audioOutputBluetoothText)
            }

            WebRtcAudioManager.AudioDevice.SPEAKER_PHONE -> {
                viewThemeUtils.platform.colorImageView(
                    dialogAudioOutputBinding.audioOutputSpeakerIcon,
                    ColorRole.PRIMARY
                )
                viewThemeUtils.platform
                    .colorPrimaryTextViewElementDarkMode(dialogAudioOutputBinding.audioOutputSpeakerText)
            }

            WebRtcAudioManager.AudioDevice.EARPIECE -> {
                viewThemeUtils.platform.colorImageView(
                    dialogAudioOutputBinding.audioOutputEarspeakerIcon,
                    ColorRole.PRIMARY
                )
                viewThemeUtils.platform
                    .colorPrimaryTextViewElementDarkMode(dialogAudioOutputBinding.audioOutputEarspeakerText)
            }

            WebRtcAudioManager.AudioDevice.WIRED_HEADSET -> {
                viewThemeUtils.platform
                    .colorImageView(dialogAudioOutputBinding.audioOutputWiredHeadsetIcon, ColorRole.PRIMARY)
                viewThemeUtils.platform
                    .colorPrimaryTextViewElementDarkMode(dialogAudioOutputBinding.audioOutputWiredHeadsetText)
            }

            else -> Log.d(TAG, "AudioOutputDialog doesn't know this AudioDevice")
        }
    }

    private fun initClickListeners() {
        dialogAudioOutputBinding.audioOutputBluetooth.setOnClickListener {
            callActivity.setAudioOutputChannel(WebRtcAudioManager.AudioDevice.BLUETOOTH)
            dismiss()
        }

        dialogAudioOutputBinding.audioOutputSpeaker.setOnClickListener {
            callActivity.setAudioOutputChannel(WebRtcAudioManager.AudioDevice.SPEAKER_PHONE)
            dismiss()
        }

        dialogAudioOutputBinding.audioOutputEarspeaker.setOnClickListener {
            callActivity.setAudioOutputChannel(WebRtcAudioManager.AudioDevice.EARPIECE)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }

    companion object {
        private const val TAG = "AudioOutputDialog"
    }
}
