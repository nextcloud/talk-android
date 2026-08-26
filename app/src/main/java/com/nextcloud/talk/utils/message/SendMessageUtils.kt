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

    /**
     * Builds a referenceId for a file shared as part of an upload batch, in the cross-client
     * format `sha256(uploadId)[0:60]-order`, so that clients (including this one) can recognize
     * files uploaded together and render them as a single grouped message.
     * See https://github.com/nextcloud/spreed/pull/19040
     */
    fun generateGroupedReferenceId(uploadId: String, order: Int): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashHex = digest.digest(uploadId.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return hashHex.take(GROUPED_REFERENCE_ID_HASH_LENGTH) + "-" + order.toString().padStart(ORDER_PADDING, '0')
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

    companion object {
        private const val GROUPED_REFERENCE_ID_HASH_LENGTH = 60
        private const val ORDER_PADDING = 3
    }
}
