package com.nextcloud.talk.adapters.messages

import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet

data class MessagePayload(
    var currentConversation: Conversation,
    val profileBottomSheet: ProfileBottomSheet
)
