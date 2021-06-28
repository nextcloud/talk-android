package com.nextcloud.talk.adapters.messages

import com.nextcloud.talk.models.json.chat.ChatMessage

interface VoiceMessageInterface {
    fun updateMediaPlayerProgressBySlider(message : ChatMessage, progress : Int)
}