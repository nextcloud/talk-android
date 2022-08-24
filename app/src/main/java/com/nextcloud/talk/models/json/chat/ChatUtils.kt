/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 * @author Marcel Hibbe
 * Copyright (C) 2021 Marcel Hibbe <dev@mhibbe.de>
 * @author Tim Krüger
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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
 */

package com.nextcloud.talk.models.json.chat

class ChatUtils {
    companion object {
        fun getParsedMessage(message: String?, messageParameters: HashMap<String?, HashMap<String?, String?>>?):
            String? {
            if (messageParameters != null && messageParameters.size > 0) {
                return parse(messageParameters, message)
            }
            return message
        }

        @Suppress("Detekt.ComplexMethod")
        private fun parse(
            messageParameters: HashMap<String?, HashMap<String?, String?>>,
            message: String?
        ): String? {
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
