package com.nextcloud.talk.adapters.messages

import com.nextcloud.talk.models.json.chat.ChatMessage

interface ReactionsInterface {
    fun onClickReactions(message: ChatMessage)
}