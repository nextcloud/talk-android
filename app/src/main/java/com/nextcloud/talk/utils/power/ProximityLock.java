/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.power;

import android.annotation.SuppressLint;
import android.os.PowerManager;

import java.util.Optional;

class ProximityLock {
    private final Optional<PowerManager.WakeLock> proximityLock;

    ProximityLock(PowerManager pm) {
        proximityLock = getProximityLock(pm);
    }

    private Optional<PowerManager.WakeLock> getProximityLock(PowerManager powerManager) {
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            return Optional.ofNullable(powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "nctalk:proximitylock"));
        } else {
            return Optional.empty();
        }
    }

    @SuppressLint("WakelockTimeout")
    void acquire() {
        if (!proximityLock.isPresent() || proximityLock.get().isHeld()) {
            return;
        }

        proximityLock.get().acquire();
    }

    void release() {
        if (!proximityLock.isPresent() || !proximityLock.get().isHeld()) {
            return;
        }

        proximityLock.get().release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
    }
}
