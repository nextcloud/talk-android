/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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
package com.nextcloud.talk.utils.power

import android.annotation.SuppressLint
import android.content.Context
import android.content.Context.POWER_SERVICE
import android.content.res.Configuration
import android.net.wifi.WifiManager
import android.net.wifi.WifiManager.WifiLock
import android.os.PowerManager
import android.provider.Settings
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.application.NextcloudTalkApplication.Companion.sharedApplication
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class PowerManagerUtils {
    private val fullLock: PowerManager.WakeLock
    private val partialLock: PowerManager.WakeLock
    private val wifiLock: WifiLock
    private val wifiLockEnforced: Boolean

    @JvmField
    @Inject
    var context: Context? = null
    private val proximityLock: ProximityLock
    private var proximityDisabled = false
    private var orientation: Int

    init {
        sharedApplication!!.componentApplication.inject(this)
        val pm = context!!.getSystemService(Context.POWER_SERVICE) as PowerManager
        fullLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "nctalk:fullwakelock"
        )
        partialLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nctalk:partialwakelock")
        proximityLock = ProximityLock(pm)

        // we suppress a possible leak because this is indeed application context
        @SuppressLint("WifiManagerPotentialLeak")
        val wm =
            context!!.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "nctalk:wifiwakelock")
        fullLock.setReferenceCounted(false)
        partialLock.setReferenceCounted(false)
        wifiLock.setReferenceCounted(false)
        wifiLockEnforced = isWifiPowerActiveModeEnabled(context)
        orientation = context!!.resources.configuration.orientation
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val packageName = context!!.packageName
        val pm = context!!.getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    fun setOrientation(newOrientation: Int) {
        orientation = newOrientation
        updateInCallWakeLockState()
    }

    fun updatePhoneState(state: PhoneState?) {
        when (state) {
            PhoneState.IDLE -> setWakeLockState(WakeLockState.SLEEP)
            PhoneState.PROCESSING -> setWakeLockState(WakeLockState.PARTIAL)
            PhoneState.INTERACTIVE -> setWakeLockState(WakeLockState.FULL)
            PhoneState.WITH_PROXIMITY_SENSOR_LOCK -> {
                proximityDisabled = false
                updateInCallWakeLockState()
            }

            PhoneState.WITHOUT_PROXIMITY_SENSOR_LOCK -> {
                proximityDisabled = true
                updateInCallWakeLockState()
            }

            else -> {}
        }
    }

    private fun updateInCallWakeLockState() {
        if (orientation != Configuration.ORIENTATION_LANDSCAPE && wifiLockEnforced && !proximityDisabled) {
            setWakeLockState(WakeLockState.PROXIMITY)
        } else {
            setWakeLockState(WakeLockState.FULL)
        }
    }

    private fun isWifiPowerActiveModeEnabled(context: Context?): Boolean {
        val wifiPowerActiveMode = Settings.Secure.getInt(context!!.contentResolver, "wifi_pwr_active_mode", -1)
        return wifiPowerActiveMode != 0
    }

    @SuppressLint("WakelockTimeout")
    @Synchronized
    private fun setWakeLockState(newState: WakeLockState) {
        when (newState) {
            WakeLockState.FULL -> {
                if (!fullLock.isHeld) {
                    fullLock.acquire()
                }
                if (!partialLock.isHeld) {
                    partialLock.acquire()
                }
                if (!wifiLock.isHeld) {
                    wifiLock.acquire()
                }
                proximityLock.release()
            }

            WakeLockState.PARTIAL -> {
                if (!partialLock.isHeld) {
                    partialLock.acquire()
                }
                if (!wifiLock.isHeld) {
                    wifiLock.acquire()
                }
                fullLock.release()
                proximityLock.release()
            }

            WakeLockState.SLEEP -> {
                fullLock.release()
                partialLock.release()
                wifiLock.release()
                proximityLock.release()
            }

            WakeLockState.PROXIMITY -> {
                if (!partialLock.isHeld) {
                    partialLock.acquire()
                }
                if (!wifiLock.isHeld) {
                    wifiLock.acquire()
                }
                fullLock.release()
                proximityLock.acquire()
            }

            else -> {}
        }
    }

    enum class PhoneState {
        IDLE,
        PROCESSING,

        // used when the phone is active but before the user should be alerted.
        INTERACTIVE,
        WITHOUT_PROXIMITY_SENSOR_LOCK,
        WITH_PROXIMITY_SENSOR_LOCK
    }

    enum class WakeLockState {
        FULL,
        PARTIAL,
        SLEEP,
        PROXIMITY
    }

    companion object {
        private val TAG = PowerManagerUtils::class.java.simpleName
    }
}
