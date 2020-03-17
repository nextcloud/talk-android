package com.nextcloud.talk.newarch.features.chat

import androidx.lifecycle.LiveData
import com.nextcloud.talk.newarch.features.contactsflow.ParticipantElement
import com.otaliastudios.elements.extensions.MainSource

class ChatViewSource<T : ChatElement>(loadingIndicatorsEnabled: Boolean = true, errorIndicatorEnabled: Boolean = false, emptyIndicatorEnabled: Boolean = false) : MainSource<T>(loadingIndicatorsEnabled, errorIndicatorEnabled, emptyIndicatorEnabled) {
    override fun areItemsTheSame(first: T, second: T): Boolean {
        TODO("Not yet implemented")
    }

}