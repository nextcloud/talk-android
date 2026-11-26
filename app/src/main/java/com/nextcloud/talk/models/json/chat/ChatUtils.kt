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

        /**
         * The message as it is typed to send it again, e.g. in a forward: mentions become the "@" syntax the server
         * resolves to a mention, the other parameters their names as in [getParsedMessage]. A mention without a
         * "mention-id", sent by an older server, and the mention of a whole conversation become the plain name, so
         * neither a part of the name is taken for another mention nor everybody of the target conversation is
         * notified.
         */
        fun getMessageToResend(
            message: String?,
            messageParameters: HashMap<String?, HashMap<String?, String?>>?
        ): String? {
            var resultMessage = message
            messageParameters?.forEach { (key, parameter) ->
                val type = parameter?.get("type")
                if (parameter != null && type in MENTION_TYPES) {
                    val mentionId = parameter["mention-id"]
                    val text = if (type != "call" && !mentionId.isNullOrEmpty()) {
                        mentionSyntax(mentionId)
                    } else {
                        parameter["name"].orEmpty()
                    }
                    resultMessage = resultMessage?.replace("{$key}", text)
                }
            }
            return getParsedMessage(resultMessage, messageParameters)
        }

        /** How a mention of [mentionId] is typed: quoted when the id holds a space, an "@" or a type prefix. */
        fun mentionSyntax(mentionId: String): String {
            val shouldQuote = mentionId.contains(" ") ||
                mentionId.contains("@") ||
                QUOTED_MENTION_PREFIXES.any { mentionId.startsWith(it) }
            return if (shouldQuote) "@\"$mentionId\"" else "@$mentionId"
        }

        private val MENTION_TYPES = setOf("user", "guest", "call", "email", "user-group", "circle")
        private val QUOTED_MENTION_PREFIXES = listOf("guest/", "group/", "email/", "team/")

        @Suppress("Detekt.ComplexMethod", "Detekt.ComplexCondition")
        private fun parse(messageParameters: HashMap<String?, HashMap<String?, String?>>, message: String?): String? {
            var resultMessage = message
            for (key in messageParameters.keys) {
                val individualHashMap = messageParameters[key]

                if (individualHashMap != null) {
                    val type = individualHashMap["type"]
                    resultMessage = if (type == "user" ||
                        type == "guest" ||
                        type == "call" ||
                        type == "email" ||
                        type == "user-group" ||
                        type == "circle"
                    ) {
                        resultMessage?.replace("{$key}", "@" + individualHashMap["name"])
                    } else if (type == "geo-location") {
                        individualHashMap["name"]
                    } else if (individualHashMap.containsKey("link")) {
                        if (type == "file") {
                            resultMessage?.replace("{$key}", individualHashMap["name"].toString())
                        } else {
                            individualHashMap["name"]?.let {
                                resultMessage?.replace(
                                    "{$key}",
                                    individualHashMap["name"]!!
                                )
                            }
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
