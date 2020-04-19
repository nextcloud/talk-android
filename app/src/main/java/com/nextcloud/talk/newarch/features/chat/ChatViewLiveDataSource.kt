package com.nextcloud.talk.newarch.features.chat

import androidx.lifecycle.LiveData
import com.nextcloud.talk.models.json.chat.ChatMessage
import com.otaliastudios.elements.Element
import com.otaliastudios.elements.Page
import com.otaliastudios.elements.Source
import com.otaliastudios.elements.extensions.MainSource

class ChatViewLiveDataSource<T : ChatElement>(private val data: LiveData<List<T>>, loadingIndicatorsEnabled: Boolean = true, errorIndicatorEnabled: Boolean = false, emptyIndicatorEnabled: Boolean = false) : MainSource<T>(loadingIndicatorsEnabled, errorIndicatorEnabled, emptyIndicatorEnabled) {
    override fun onPageOpened(page: Page, dependencies: List<Element<*>>) {
        super.onPageOpened(page, dependencies)
        if (page.previous() == null) {
            postResult(page, data)
        }
    }

    override fun dependsOn(source: Source<*>): Boolean {
        return false
    }

    override fun areContentsTheSame(first: T, second: T): Boolean {
        return first == second
    }

    override fun getElementType(data: T): Int {
        return data.elementType.ordinal
    }

    override fun areItemsTheSame(first: T, second: T): Boolean {
        if (first.elementType != second.elementType) {
            return false
        }

        if (first.data is ChatMessage && second.data is ChatMessage) {
            return first.data.jsonMessageId == second.data.jsonMessageId || first.data.referenceId == second.data.referenceId
        }

        return false
    }
}