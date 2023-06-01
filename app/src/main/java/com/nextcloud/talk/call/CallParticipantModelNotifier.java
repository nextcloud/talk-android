/*
 * Nextcloud Talk application
 *
 * @author Daniel Calviño Sánchez
 * Copyright (C) 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
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
package com.nextcloud.talk.call;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Helper class to register and notify CallParticipantModel.Observers.
 * <p>
 * This class is only meant for internal use by CallParticipantModel; observers must register themselves against a
 * CallParticipantModel rather than against a CallParticipantModelNotifier.
 */
class CallParticipantModelNotifier {

    private final List<CallParticipantModelObserverOn> callParticipantModelObserversOn = new ArrayList<>();

    /**
     * Helper class to associate a CallParticipantModel.Observer with a Handler.
     */
    private static class CallParticipantModelObserverOn {
        public final CallParticipantModel.Observer observer;
        public final Handler handler;

        private CallParticipantModelObserverOn(CallParticipantModel.Observer observer, Handler handler) {
            this.observer = observer;
            this.handler = handler;
        }
    }

    public synchronized void addObserver(CallParticipantModel.Observer observer, Handler handler) {
        if (observer == null) {
            throw new IllegalArgumentException("CallParticipantModel.Observer can not be null");
        }

        removeObserver(observer);

        callParticipantModelObserversOn.add(new CallParticipantModelObserverOn(observer, handler));
    }

    public synchronized void removeObserver(CallParticipantModel.Observer observer) {
        Iterator<CallParticipantModelObserverOn> it = callParticipantModelObserversOn.iterator();
        while (it.hasNext()) {
            CallParticipantModelObserverOn observerOn = it.next();

            if (observerOn.observer == observer) {
                it.remove();

                return;
            }
        }
    }

    public synchronized void notifyChange() {
        for (CallParticipantModelObserverOn observerOn : new ArrayList<>(callParticipantModelObserversOn)) {
            if (observerOn.handler == null || observerOn.handler.getLooper() == Looper.myLooper()) {
                observerOn.observer.onChange();
            } else {
                observerOn.handler.post(() -> {
                    observerOn.observer.onChange();
                });
            }
        }
    }

    public synchronized void notifyReaction(String reaction) {
        for (CallParticipantModelObserverOn observerOn : new ArrayList<>(callParticipantModelObserversOn)) {
            if (observerOn.handler == null || observerOn.handler.getLooper() == Looper.myLooper()) {
                observerOn.observer.onReaction(reaction);
            } else {
                observerOn.handler.post(() -> {
                    observerOn.observer.onReaction(reaction);
                });
            }
        }
    }
}
