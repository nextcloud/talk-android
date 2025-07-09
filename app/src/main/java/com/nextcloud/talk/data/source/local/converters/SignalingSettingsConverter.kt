/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2020 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.data.source.local.converters

import androidx.room.TypeConverter
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettings

class SignalingSettingsConverter {

    @TypeConverter
    fun fromSignalingSettingsToString(signalingSettings: SignalingSettings?): String =
        if (signalingSettings == null) {
            ""
        } else {
            LoganSquare.serialize(signalingSettings)
        }

    @TypeConverter
    fun fromStringToSignalingSettings(value: String): SignalingSettings? {
        return if (value.isBlank()) {
            null
        } else {
            return LoganSquare.parse(value, SignalingSettings::class.java)
        }
    }
}
