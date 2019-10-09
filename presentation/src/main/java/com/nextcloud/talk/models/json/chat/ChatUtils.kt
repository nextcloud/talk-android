/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

import java.util.*

object ChatUtils {

    fun getParsedMessage(message: String, messageParameters: HashMap<String, HashMap<String, String>>?): String {
        var message = message
        if (messageParameters != null && messageParameters.size > 0) {
            for (key in messageParameters.keys) {
                val individualHashMap = messageParameters[key]
                if (individualHashMap!!["type"] == "user" || individualHashMap["type"] == "guest" || individualHashMap["type"] == "call") {
                    message = message.replace("\\{$key\\}".toRegex(), "@" + messageParameters[key]!!["name"]!!)
                } else if (individualHashMap["type"] == "file") {
                    message = message.replace("\\{$key\\}".toRegex(), messageParameters[key]!!["name"])
                }
            }
        }


        return message
    }
}
