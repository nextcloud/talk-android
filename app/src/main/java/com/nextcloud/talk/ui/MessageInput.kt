/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.SeekBar
import android.widget.TextView
import androidx.emoji2.widget.EmojiEditText
import com.google.android.material.button.MaterialButton
import com.nextcloud.talk.R
import com.stfalcon.chatkit.messages.MessageInput

class MessageInput : MessageInput {
    lateinit var audioRecordDuration: Chronometer
    lateinit var recordAudioButton: ImageButton
    lateinit var submitThreadButton: ImageButton
    lateinit var slideToCancelDescription: TextView
    lateinit var microphoneEnabledInfo: ImageView
    lateinit var microphoneEnabledInfoBackground: ImageView
    lateinit var smileyButton: ImageButton
    lateinit var deleteVoiceRecording: ImageView
    lateinit var sendVoiceRecording: ImageView
    lateinit var micInputCloud: MicInputCloud
    lateinit var playPauseBtn: MaterialButton
    lateinit var editMessageButton: ImageButton
    lateinit var seekBar: SeekBar

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        audioRecordDuration = findViewById(R.id.audioRecordDuration)
        recordAudioButton = findViewById(R.id.recordAudioButton)
        submitThreadButton = findViewById(R.id.submitThreadButton)
        slideToCancelDescription = findViewById(R.id.slideToCancelDescription)
        microphoneEnabledInfo = findViewById(R.id.microphoneEnabledInfo)
        microphoneEnabledInfoBackground = findViewById(R.id.microphoneEnabledInfoBackground)
        smileyButton = findViewById(R.id.smileyButton)
        deleteVoiceRecording = findViewById(R.id.deleteVoiceRecording)
        sendVoiceRecording = findViewById(R.id.sendVoiceRecording)
        micInputCloud = findViewById(R.id.micInputCloud)
        playPauseBtn = findViewById(R.id.playPauseBtn)
        seekBar = findViewById(R.id.seekbar)
        editMessageButton = findViewById(R.id.editMessageButton)
    }

    var messageInput: EmojiEditText
        get() = super.messageInput
        set(messageInput) {
            super.messageInput = messageInput
        }

    var attachmentButton: ImageButton
        get() = super.attachmentButton
        set(attachmentButton) {
            super.attachmentButton = attachmentButton
        }

    var messageSendButton: ImageButton
        get() = super.messageSendButton
        set(messageSendButton) {
            super.messageSendButton = messageSendButton
        }
}
