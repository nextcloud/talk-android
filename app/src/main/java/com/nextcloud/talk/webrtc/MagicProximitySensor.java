/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
 * Original code:
 *
 *
 * Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 * Use of this source code is governed by a BSD-style license
 * that can be found in the LICENSE file in the root of the source
 * tree. An additional intellectual property rights grant can be found
 * in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package com.nextcloud.talk.webrtc;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.util.Log;
import org.webrtc.ThreadUtils;

/**
 * MagicProximitySensor manages functions related to the proximity sensor in
 * the app.
 * On most device, the proximity sensor is implemented as a boolean-sensor.
 * It returns just two values "NEAR" or "FAR". Thresholding is done on the LUX
 * value i.e. the LUX value of the light sensor is compared with a threshold.
 * A LUX-value more than the threshold means the proximity sensor returns "FAR".
 * Anything less than the threshold value and the sensor  returns "NEAR".
 */
public class MagicProximitySensor implements SensorEventListener {
    private static final String TAG = "MagicProximitySensor";

    // This class should be created, started and stopped on one thread
    // (e.g. the main thread). We use |nonThreadSafe| to ensure that this is
    // the case. Only active when |DEBUG| is set to true.
    private final ThreadUtils.ThreadChecker threadChecker = new ThreadUtils.ThreadChecker();

    private final Runnable onSensorStateListener;
    private final SensorManager sensorManager;
    private Sensor proximitySensor = null;
    private boolean lastStateReportIsNear = false;

    private MagicProximitySensor(Context context, Runnable sensorStateListener) {
        onSensorStateListener = sensorStateListener;
        sensorManager = ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
    }

    /**
     * Construction
     */
    static MagicProximitySensor create(Context context, Runnable sensorStateListener) {
        return new MagicProximitySensor(context, sensorStateListener);
    }

    /**
     * Activate the proximity sensor. Also do initialization if called for the
     * first time.
     */
    public boolean start() {
        threadChecker.checkIsOnValidThread();
        if (!initDefaultSensor()) {
            // Proximity sensor is not supported on this device.
            return false;
        }
        sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        return true;
    }

    /**
     * Deactivate the proximity sensor.
     */
    void stop() {
        threadChecker.checkIsOnValidThread();
        if (proximitySensor == null) {
            return;
        }
        sensorManager.unregisterListener(this, proximitySensor);
    }

    /**
     * Getter for last reported state. Set to true if "near" is reported.
     */
    boolean sensorReportsNearState() {
        threadChecker.checkIsOnValidThread();
        return lastStateReportIsNear;
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        threadChecker.checkIsOnValidThread();
        if (sensor.getType() == Sensor.TYPE_PROXIMITY &&
                accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            Log.e(TAG, "The values returned by this sensor cannot be trusted");
        }
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        threadChecker.checkIsOnValidThread();
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            // As a best practice; do as little as possible within this method and
            // avoid blocking.
            float distanceInCentimeters = event.values[0];
            if (distanceInCentimeters < proximitySensor.getMaximumRange()) {
                Log.d(TAG, "Proximity sensor => NEAR state");
                lastStateReportIsNear = true;
            } else {
                Log.d(TAG, "Proximity sensor => FAR state");
                lastStateReportIsNear = false;
            }

            // Report about new state to listening client. Client can then call
            // sensorReportsNearState() to query the current state (NEAR or FAR).
            if (onSensorStateListener != null) {
                onSensorStateListener.run();
            }
        }
    }

    /**
     * Get default proximity sensor if it exists. Tablet devices (e.g. Nexus 7)
     * does not support this type of sensor and false will be returned in such
     * cases.
     */
    private boolean initDefaultSensor() {
        if (proximitySensor != null) {
            return true;
        }
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (proximitySensor == null) {
            return false;
        }
        logProximitySensorInfo();
        return true;
    }

    /**
     * Helper method for logging information about the proximity sensor.
     */
    private void logProximitySensorInfo() {
        if (proximitySensor == null) {
            return;
        }
        StringBuilder info = new StringBuilder("Proximity sensor: ");
        info.append("name=").append(proximitySensor.getName())
                .append(", vendor: ").append(proximitySensor.getVendor())
                .append(", power: ").append(proximitySensor.getPower())
                .append(", resolution: ").append(proximitySensor.getResolution())
                .append(", max range: ").append(proximitySensor.getMaximumRange())
                .append(", min delay: ").append(proximitySensor.getMinDelay());
        // Added in API level 20.
        info.append(", type: ").append(proximitySensor.getStringType());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Added in API level 21.
            info.append(", max delay: ").append(proximitySensor.getMaxDelay())
                    .append(", reporting mode: ").append(proximitySensor.getReportingMode())
                    .append(", isWakeUpSensor: ").append(proximitySensor.isWakeUpSensor());
        }
        Log.d(TAG, info.toString());
    }
}
