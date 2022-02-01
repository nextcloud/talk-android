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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.databinding.DialogAudioOutputBinding
import com.nextcloud.talk.webrtc.MagicAudioManager

class AudioOutputDialog(val callActivity: CallActivity) : BottomSheetDialog(callActivity) {

    private lateinit var dialogAudioOutputBinding: DialogAudioOutputBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogAudioOutputBinding = DialogAudioOutputBinding.inflate(layoutInflater)
        setContentView(dialogAudioOutputBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        updateOutputDeviceList()
        initClickListeners()
    }

    fun updateOutputDeviceList() {
        if (callActivity.audioManager?.audioDevices?.contains(MagicAudioManager.AudioDevice.BLUETOOTH) == false) {
            dialogAudioOutputBinding.audioOutputBluetooth.visibility = View.GONE
        } else {
            dialogAudioOutputBinding.audioOutputBluetooth.visibility = View.VISIBLE
        }

        if (callActivity.audioManager?.audioDevices?.contains(MagicAudioManager.AudioDevice.EARPIECE) == false) {
            dialogAudioOutputBinding.audioOutputEarspeaker.visibility = View.GONE
        } else {
            dialogAudioOutputBinding.audioOutputEarspeaker.visibility = View.VISIBLE
        }

        if (callActivity.audioManager?.audioDevices?.contains(MagicAudioManager.AudioDevice.SPEAKER_PHONE) == false) {
            dialogAudioOutputBinding.audioOutputSpeaker.visibility = View.GONE
        } else {
            dialogAudioOutputBinding.audioOutputSpeaker.visibility = View.VISIBLE
        }

        if (callActivity.audioManager?.currentAudioDevice?.equals(
                MagicAudioManager.AudioDevice.WIRED_HEADSET
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
            MagicAudioManager.AudioDevice.BLUETOOTH -> {
                dialogAudioOutputBinding.audioOutputBluetoothIcon.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.colorPrimary
                    ),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                dialogAudioOutputBinding.audioOutputBluetoothText.setTextColor(
                    callActivity.resources.getColor(R.color.colorPrimary)
                )
            }

            MagicAudioManager.AudioDevice.SPEAKER_PHONE -> {
                dialogAudioOutputBinding.audioOutputSpeakerIcon.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.colorPrimary
                    ),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                dialogAudioOutputBinding.audioOutputSpeakerText.setTextColor(
                    callActivity.resources.getColor(R.color.colorPrimary)
                )
            }

            MagicAudioManager.AudioDevice.EARPIECE -> {
                dialogAudioOutputBinding.audioOutputEarspeakerIcon.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.colorPrimary
                    ),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                dialogAudioOutputBinding.audioOutputEarspeakerText.setTextColor(
                    callActivity.resources.getColor(R.color.colorPrimary)
                )
            }

            MagicAudioManager.AudioDevice.WIRED_HEADSET -> {
                dialogAudioOutputBinding.audioOutputWiredHeadsetIcon.setColorFilter(
                    ContextCompat.getColor(
                        context,
                        R.color.colorPrimary
                    ),
                    android.graphics.PorterDuff.Mode.SRC_IN
                )
                dialogAudioOutputBinding.audioOutputWiredHeadsetText.setTextColor(
                    callActivity.resources.getColor(R.color.colorPrimary)
                )
            }

            else -> Log.d(TAG, "AudioOutputDialog doesn't know this AudioDevice")
        }
    }

    private fun initClickListeners() {
        dialogAudioOutputBinding.audioOutputBluetooth.setOnClickListener {
            callActivity.setAudioOutputChannel(MagicAudioManager.AudioDevice.BLUETOOTH)
            dismiss()
        }

        dialogAudioOutputBinding.audioOutputSpeaker.setOnClickListener {
            callActivity.setAudioOutputChannel(MagicAudioManager.AudioDevice.SPEAKER_PHONE)
            dismiss()
        }

        dialogAudioOutputBinding.audioOutputEarspeaker.setOnClickListener {
            callActivity.setAudioOutputChannel(MagicAudioManager.AudioDevice.EARPIECE)
            dismiss()
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = findViewById<View>(R.id.design_bottom_sheet)
        val behavior = BottomSheetBehavior.from(bottomSheet as View)
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
    }

    companion object {
        private const val TAG = "AudioOutputDialog"
    }
}
