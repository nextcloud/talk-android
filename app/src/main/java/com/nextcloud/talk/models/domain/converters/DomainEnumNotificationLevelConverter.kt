/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2024  Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.domain.converters

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter
import com.nextcloud.talk.models.domain.NotificationLevel

class DomainEnumNotificationLevelConverter : IntBasedTypeConverter<NotificationLevel>() {
    override fun getFromInt(i: Int): NotificationLevel {
        return when (i) {
            DEFAULT -> NotificationLevel.DEFAULT
            ALWAYS -> NotificationLevel.ALWAYS
            MENTION -> NotificationLevel.MENTION
            NEVER -> NotificationLevel.NEVER
            else -> NotificationLevel.DEFAULT
        }
    }

    override fun convertToInt(`object`: NotificationLevel): Int {
        return when (`object`) {
            NotificationLevel.DEFAULT -> DEFAULT
            NotificationLevel.ALWAYS -> ALWAYS
            NotificationLevel.MENTION -> MENTION
            NotificationLevel.NEVER -> NEVER
            else -> DEFAULT
        }
    }

    companion object {
        private const val DEFAULT: Int = 0
        private const val ALWAYS: Int = 1
        private const val MENTION: Int = 2
        private const val NEVER: Int = 3
    }
}
