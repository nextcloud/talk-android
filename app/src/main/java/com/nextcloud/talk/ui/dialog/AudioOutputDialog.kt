/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.CallActivity
import com.nextcloud.talk.databinding.DialogAudioOutputBinding

class AudioOutputDialog(val callActivity: CallActivity) : BottomSheetDialog(callActivity) {

    private lateinit var dialogAudioOutputBinding: DialogAudioOutputBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dialogAudioOutputBinding = DialogAudioOutputBinding.inflate(layoutInflater)
        setContentView(dialogAudioOutputBinding.root)
        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)


        dialogAudioOutputBinding.audioOutputBluetooth.setOnClickListener {
            Log.d(TAG, "bluetooth button clicked")
            callActivity.setAudioOutputIcon(dialogAudioOutputBinding.audioOutputBluetoothIcon.drawable)
            dismiss()
        }

        dialogAudioOutputBinding.audioOutputSpeaker.setOnClickListener {
            Log.d(TAG, "speaker button clicked")
            callActivity.setAudioOutputIcon(dialogAudioOutputBinding.audioOutputSpeakerIcon.drawable)

            dismiss()
        }

        dialogAudioOutputBinding.audioOutputEarspeaker.setOnClickListener {
            Log.d(TAG, "earspeaker button clicked")
            callActivity.setAudioOutputIcon(dialogAudioOutputBinding.audioOutputEarspeakerIcon.drawable)

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
