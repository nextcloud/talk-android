/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Daniel Calviño Sánchez <danxuliu@gmail.com>
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.events;

public class ProximitySensorEvent {
    private final ProximitySensorEventType proximitySensorEventType;

    public ProximitySensorEvent(ProximitySensorEventType proximitySensorEventType) {
        this.proximitySensorEventType = proximitySensorEventType;
    }

    public ProximitySensorEventType getProximitySensorEventType() {
        return this.proximitySensorEventType;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ProximitySensorEvent)) {
            return false;
        }
        final ProximitySensorEvent other = (ProximitySensorEvent) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$proximitySensorEventType = this.getProximitySensorEventType();
        final Object other$proximitySensorEventType = other.getProximitySensorEventType();
        if (this$proximitySensorEventType == null ? other$proximitySensorEventType != null : !this$proximitySensorEventType.equals(other$proximitySensorEventType)) {
            return false;
        }

        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ProximitySensorEvent;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $proximitySensorEventType = this.getProximitySensorEventType();
        result = result * PRIME + ($proximitySensorEventType == null ? 43 : $proximitySensorEventType.hashCode());
        return result;
    }

    public String toString() {
        return "ProximitySensorEvent(proximitySensorEventType=" + this.getProximitySensorEventType() + ")";
    }

    public enum ProximitySensorEventType {
        SENSOR_FAR, SENSOR_NEAR
    }
}
