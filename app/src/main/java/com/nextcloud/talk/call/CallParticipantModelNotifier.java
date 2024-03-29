/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
