/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.utils.power;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.PowerManager;
import androidx.annotation.RequiresApi;

import java.util.Optional;

class ProximityLock {
    private final Optional<PowerManager.WakeLock> proximityLock;

    @RequiresApi(api = Build.VERSION_CODES.N)
    ProximityLock(PowerManager pm) {
        proximityLock = getProximityLock(pm);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private Optional<PowerManager.WakeLock> getProximityLock(PowerManager powerManager) {
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            return Optional.ofNullable(powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "nctalk:proximitylock"));
        } else {
            return Optional.empty();
        }
    }

    @SuppressLint("WakelockTimeout")
    @RequiresApi(api = Build.VERSION_CODES.N)
    void acquire() {
        if (!proximityLock.isPresent() || proximityLock.get().isHeld()) {
            return;
        }

        proximityLock.get().acquire();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    void release() {
        if (!proximityLock.isPresent() || !proximityLock.get().isHeld()) {
            return;
        }

        proximityLock.get().release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
    }
}
