/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Tim Krüger <t@timkrueger.me>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.chat

@Suppress("UtilityClassWithPublicConstructor")
class ChatUtils {
    companion object {
        fun getParsedMessage(
            message: String?,
            messageParameters: HashMap<String?, HashMap<String?, String?>>?
        ): String? {
            if (messageParameters != null && messageParameters.size > 0) {
                return parse(messageParameters, message)
            }
            return message
        }

        @Suppress("Detekt.ComplexMethod")
        private fun parse(messageParameters: HashMap<String?, HashMap<String?, String?>>, message: String?): String? {
            var resultMessage = message
            for (key in messageParameters.keys) {
                val individualHashMap = messageParameters[key]

                if (individualHashMap != null) {
                    val type = individualHashMap["type"]
                    resultMessage = if (type == "user" || type == "guest" || type == "call") {
                        resultMessage?.replace("{$key}", "@" + individualHashMap["name"])
                    } else if (type == "geo-location") {
                        individualHashMap["name"]
                    } else if (individualHashMap?.containsKey("link") == true) {
                        if (type == "file") {
                            resultMessage?.replace("{$key}", individualHashMap["name"].toString())
                        } else {
                            individualHashMap["link"].toString()
                        }
                    } else {
                        individualHashMap["name"]?.let { resultMessage?.replace("{$key}", it) }
                    }
                }
            }
            return resultMessage
        }
    }
}
