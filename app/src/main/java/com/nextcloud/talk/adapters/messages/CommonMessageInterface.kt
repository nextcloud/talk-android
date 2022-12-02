package com.nextcloud.talk.adapters.messages

import com.nextcloud.talk.models.json.chat.ChatMessage

interface CommonMessageInterface {
    fun onLongClickReactions(chatMessage: ChatMessage)
    fun onClickReaction(chatMessage: ChatMessage, emoji: String)
    fun onOpenMessageActionsDialog(chatMessage: ChatMessage)
}
