/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.source.local.converters

import androidx.room.TypeConverter
import com.nextcloud.talk.data.database.model.SendStatus

class SendStatusConverter {
    @TypeConverter
    fun fromStatus(value: SendStatus): String {
        return value.name
    }

    @TypeConverter
    fun toStatus(value: String): SendStatus {
        return SendStatus.valueOf(value)
    }
}
