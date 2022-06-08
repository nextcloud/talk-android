package com.nextcloud.talk.adapters.messages

import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet

data class MessagePayload(
    val roomToken: String,
    val profileBottomSheet: ProfileBottomSheet
)
