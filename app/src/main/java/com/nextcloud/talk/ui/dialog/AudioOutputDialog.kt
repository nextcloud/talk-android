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
import com.nextcloud.talk.databinding.DialogAudioOutputBinding
import com.nextcloud.talk.ui.theme.ServerTheme
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.webrtc.WebRtcAudioManager
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class AudioOutputDialog(val callActivity: CallActivity) : BottomSheetDialog(callActivity) {

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var serverTheme: ServerTheme

    private lateinit var dialogAudioOutputBinding: DialogAudioOutputBinding

    init {
        NextcloudTalkApplication.sharedApplication?.componentApplication?.inject(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogAudioOutputBinding = DialogAudioOutputBinding.inflate(layoutInflater)
        setContentView(dialogAudioOutputBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

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
                viewThemeUtils.colorImageView(dialogAudioOutputBinding.audioOutputBluetoothIcon)
                dialogAudioOutputBinding.audioOutputBluetoothText.setTextColor(serverTheme.primaryColor)
            }

            WebRtcAudioManager.AudioDevice.SPEAKER_PHONE -> {
                viewThemeUtils.colorImageView(dialogAudioOutputBinding.audioOutputSpeakerIcon)
                dialogAudioOutputBinding.audioOutputSpeakerText.setTextColor(serverTheme.primaryColor)
            }

            WebRtcAudioManager.AudioDevice.EARPIECE -> {
                viewThemeUtils.colorImageView(dialogAudioOutputBinding.audioOutputEarspeakerIcon)
                dialogAudioOutputBinding.audioOutputEarspeakerText.setTextColor(serverTheme.primaryColor)
            }

            WebRtcAudioManager.AudioDevice.WIRED_HEADSET -> {
                viewThemeUtils.colorImageView(dialogAudioOutputBinding.audioOutputWiredHeadsetIcon)
                dialogAudioOutputBinding.audioOutputWiredHeadsetText.setTextColor(serverTheme.primaryColor)
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
