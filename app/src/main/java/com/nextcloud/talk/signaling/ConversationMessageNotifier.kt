/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
    fun notifyStartTyping(sessionId: String?) {
        for (listener in ArrayList(conversationMessageListeners)) {
            listener.onStartTyping(sessionId)
        }
    }

    fun notifyStopTyping(sessionId: String?) {
        for (listener in ArrayList(conversationMessageListeners)) {
            listener.onStopTyping(sessionId)
        }
    }
}
