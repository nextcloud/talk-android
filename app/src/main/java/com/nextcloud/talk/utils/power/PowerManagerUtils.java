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
 *
 * This class is in part based on the code from the great people that wrote Signal
 * https://github.com/signalapp/Signal-Android/raw/f9adb4e4554a44fd65b77320e34bf4bccf7924ce/src/org/thoughtcrime/securesms/webrtc/locks/LockManager.java
 */

package com.nextcloud.talk.utils.power;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import autodagger.AutoInjector;
import com.nextcloud.talk.application.NextcloudTalkApplication;

import javax.inject.Inject;

@AutoInjector(NextcloudTalkApplication.class)

public class PowerManagerUtils {
    private static final String TAG = "PowerManagerUtils";
    private final PowerManager.WakeLock fullLock;
    private final PowerManager.WakeLock partialLock;
    private final WifiManager.WifiLock wifiLock;
    private final boolean wifiLockEnforced;
    @Inject
    Context context;
    private ProximityLock proximityLock;
    private boolean proximityDisabled = false;

    private int orientation;

    public PowerManagerUtils() {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        fullLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "nctalk:fullwakelock");
        partialLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nctalk:partialwakelock");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            proximityLock = new ProximityLock(pm);
        }

        // we suppress a possible leak because this is indeed application context
        @SuppressLint("WifiManagerPotentialLeak") WifiManager wm = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nctalk:wifiwakelock");

        fullLock.setReferenceCounted(false);
        partialLock.setReferenceCounted(false);
        wifiLock.setReferenceCounted(false);

        wifiLockEnforced = isWifiPowerActiveModeEnabled(context);
        orientation = context.getResources().getConfiguration().orientation;
    }

    public void setOrientation(int newOrientation) {
        orientation = newOrientation;
        updateInCallWakeLockState();
    }

    public void updatePhoneState(PhoneState state) {
        switch (state) {
            case IDLE:
                setWakeLockState(WakeLockState.SLEEP);
                break;
            case PROCESSING:
                setWakeLockState(WakeLockState.PARTIAL);
                break;
            case INTERACTIVE:
                setWakeLockState(WakeLockState.FULL);
                break;
            case WITH_PROXIMITY_SENSOR_LOCK:
                proximityDisabled = false;
                updateInCallWakeLockState();
                break;
            case WITHOUT_PROXIMITY_SENSOR_LOCK:
                proximityDisabled = true;
                updateInCallWakeLockState();
                break;
        }
    }

    private void updateInCallWakeLockState() {
        if (orientation != Configuration.ORIENTATION_LANDSCAPE && wifiLockEnforced && !proximityDisabled) {
            setWakeLockState(WakeLockState.PROXIMITY);
        } else {
            setWakeLockState(WakeLockState.FULL);
        }
    }

    private boolean isWifiPowerActiveModeEnabled(Context context) {
        int wifi_pwr_active_mode = Settings.Secure.getInt(context.getContentResolver(), "wifi_pwr_active_mode", -1);
        return (wifi_pwr_active_mode != 0);
    }

    @SuppressLint("WakelockTimeout")
    private synchronized void setWakeLockState(WakeLockState newState) {
        switch (newState) {
            case FULL:
                if (!fullLock.isHeld()) {
                    fullLock.acquire();
                }

                if (!partialLock.isHeld()) {
                    partialLock.acquire();
                }

                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    proximityLock.release();
                }
                break;
            case PARTIAL:
                if (!partialLock.isHeld()) {
                    partialLock.acquire();
                }

                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                }

                fullLock.release();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    proximityLock.release();
                }
                break;
            case SLEEP:
                fullLock.release();
                partialLock.release();
                wifiLock.release();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    proximityLock.release();
                }
                break;
            case PROXIMITY:
                if (!partialLock.isHeld()) {
                    partialLock.acquire();
                }

                if (!wifiLock.isHeld()) {
                    wifiLock.acquire();
                }

                fullLock.release(

                );
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    proximityLock.acquire();
                }
                break;
            default:
                // something went very very wrong
        }
    }

    public enum PhoneState {
        IDLE,
        PROCESSING,  //used when the phone is active but before the user should be alerted.
        INTERACTIVE,
        WITHOUT_PROXIMITY_SENSOR_LOCK,
        WITH_PROXIMITY_SENSOR_LOCK
    }

    public enum WakeLockState {
        FULL,
        PARTIAL,
        SLEEP,
        PROXIMITY
    }
}
