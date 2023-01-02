/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.Chronometer
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.emoji2.widget.EmojiEditText
import com.nextcloud.talk.R
import com.stfalcon.chatkit.messages.MessageInput

class MessageInput : MessageInput {
    lateinit var audioRecordDuration: Chronometer
    lateinit var recordAudioButton: ImageButton
    lateinit var slideToCancelDescription: TextView
    lateinit var microphoneEnabledInfo: ImageView
    lateinit var microphoneEnabledInfoBackground: ImageView
    lateinit var smileyButton: ImageButton

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
        slideToCancelDescription = findViewById(R.id.slideToCancelDescription)
        microphoneEnabledInfo = findViewById(R.id.microphoneEnabledInfo)
        microphoneEnabledInfoBackground = findViewById(R.id.microphoneEnabledInfoBackground)
        smileyButton = findViewById(R.id.smileyButton)
    }

    var messageInput: EmojiEditText
        get() = messageInput
        set(messageInput) {
            super.messageInput = messageInput
        }

    var attachmentButton: ImageButton
        get() = attachmentButton
        set(attachmentButton) {
            super.attachmentButton = attachmentButton
        }

    var messageSendButton: ImageButton
        get() = messageSendButton
        set(messageSendButton) {
            super.messageSendButton = messageSendButton
        }
}
