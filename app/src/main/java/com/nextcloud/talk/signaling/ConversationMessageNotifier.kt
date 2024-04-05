/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.signaling

import com.nextcloud.talk.signaling.SignalingMessageReceiver.ConversationMessageListener

internal class ConversationMessageNotifier {
    private val conversationMessageListeners: MutableSet<ConversationMessageListener> = LinkedHashSet()

    @Synchronized
    fun addListener(listener: ConversationMessageListener?) {
        requireNotNull(listener) { "conversationMessageListener can not be null" }
        conversationMessageListeners.add(listener)
    }

    @Synchronized
    fun removeListener(listener: ConversationMessageListener) {
        conversationMessageListeners.remove(listener)
    }

    @Synchronized
    fun notifyStartTyping(userId: String?, sessionId: String?) {
        for (listener in ArrayList(conversationMessageListeners)) {
            listener.onStartTyping(userId, sessionId)
        }
    }

    fun notifyStopTyping(userId: String?, sessionId: String?) {
        for (listener in ArrayList(conversationMessageListeners)) {
            listener.onStopTyping(userId, sessionId)
        }
    }
}
