package com.nextcloud.talk.adapters.messages

import com.nextcloud.talk.models.json.chat.ChatMessage

interface CommonMessageInterface {
    fun onClickReactions(chatMessage: ChatMessage)
    fun onOpenMessageActionsDialog(chatMessage: ChatMessage)
}
