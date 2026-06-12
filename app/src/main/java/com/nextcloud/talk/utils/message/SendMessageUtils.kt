/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.message

import java.security.MessageDigest
import java.util.Calendar
import java.util.UUID

class SendMessageUtils {
    fun generateReferenceId(): String {
        val randomString = UUID.randomUUID().toString()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(randomString.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    @Suppress("MagicNumber")
    fun timeOfDayMillis(timestampMillis: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestampMillis }

        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        val millis = calendar.get(Calendar.MILLISECOND)
        return (hour * 3_600_000) + (minute * 60_000) + (second * 1_000) + millis
    }
}
