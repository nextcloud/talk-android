/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Helper class to register and notify LocalCallParticipantModel.Observers.
 * <p>
 * This class is only meant for internal use by LocalCallParticipantModel; observers must register themselves against a
 * LocalCallParticipantModel rather than against a LocalCallParticipantModelNotifier.
 */
class LocalCallParticipantModelNotifier {

    private final List<LocalCallParticipantModelObserverOn> localCallParticipantModelObserversOn = new ArrayList<>();

    /**
     * Helper class to associate a LocalCallParticipantModel.Observer with a Handler.
     */
    private static class LocalCallParticipantModelObserverOn {
        public final LocalCallParticipantModel.Observer observer;
        public final Handler handler;

        private LocalCallParticipantModelObserverOn(LocalCallParticipantModel.Observer observer, Handler handler) {
            this.observer = observer;
            this.handler = handler;
        }
    }

    public synchronized void addObserver(LocalCallParticipantModel.Observer observer, Handler handler) {
        if (observer == null) {
            throw new IllegalArgumentException("LocalCallParticipantModel.Observer can not be null");
        }

        removeObserver(observer);

        localCallParticipantModelObserversOn.add(new LocalCallParticipantModelObserverOn(observer, handler));
    }

    public synchronized void removeObserver(LocalCallParticipantModel.Observer observer) {
        Iterator<LocalCallParticipantModelObserverOn> it = localCallParticipantModelObserversOn.iterator();
        while (it.hasNext()) {
            LocalCallParticipantModelObserverOn observerOn = it.next();

            if (observerOn.observer == observer) {
                it.remove();

                return;
            }
        }
    }

    public synchronized void notifyChange() {
        for (LocalCallParticipantModelObserverOn observerOn : new ArrayList<>(localCallParticipantModelObserversOn)) {
            if (observerOn.handler == null || observerOn.handler.getLooper() == Looper.myLooper()) {
                observerOn.observer.onChange();
            } else {
                observerOn.handler.post(() -> {
                    observerOn.observer.onChange();
                });
            }
        }
    }
}
