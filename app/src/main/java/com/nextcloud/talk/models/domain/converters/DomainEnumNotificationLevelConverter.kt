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
import com.nextcloud.talk.models.json.conversations.ConversationEnums

class DomainEnumNotificationLevelConverter : IntBasedTypeConverter<ConversationEnums.NotificationLevel>() {
    override fun getFromInt(i: Int): ConversationEnums.NotificationLevel =
        when (i) {
            DEFAULT -> ConversationEnums.NotificationLevel.DEFAULT
            ALWAYS -> ConversationEnums.NotificationLevel.ALWAYS
            MENTION -> ConversationEnums.NotificationLevel.MENTION
            NEVER -> ConversationEnums.NotificationLevel.NEVER
            else -> ConversationEnums.NotificationLevel.DEFAULT
        }

    override fun convertToInt(`object`: ConversationEnums.NotificationLevel): Int =
        when (`object`) {
            ConversationEnums.NotificationLevel.DEFAULT -> DEFAULT
            ConversationEnums.NotificationLevel.ALWAYS -> ALWAYS
            ConversationEnums.NotificationLevel.MENTION -> MENTION
            ConversationEnums.NotificationLevel.NEVER -> NEVER
            else -> DEFAULT
        }

    companion object {
        private const val DEFAULT: Int = 0
        private const val ALWAYS: Int = 1
        private const val MENTION: Int = 2
        private const val NEVER: Int = 3
    }
}
