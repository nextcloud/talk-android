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
package com.nextcloud.talk.signaling;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;


class ConversationMessageNotifier {

    private final Set<SignalingMessageReceiver.ConversationMessageListener> conversationMessageListeners = new LinkedHashSet<>();

    public synchronized void addListener(SignalingMessageReceiver.ConversationMessageListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("conversationMessageListener can not be null");
        }

        conversationMessageListeners.add(listener);
    }

    public synchronized void removeListener(SignalingMessageReceiver.ConversationMessageListener listener) {
        conversationMessageListeners.remove(listener);
    }

    public synchronized void notifyStartTyping(String sessionId) {
        for (SignalingMessageReceiver.ConversationMessageListener listener : new ArrayList<>(conversationMessageListeners)) {
            listener.onStartTyping(sessionId);
        }
    }

    public void notifyStopTyping(String sessionId) {
        for (SignalingMessageReceiver.ConversationMessageListener listener : new ArrayList<>(conversationMessageListeners)) {
            listener.onStopTyping(sessionId);
        }
    }
}
