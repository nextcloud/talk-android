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

package com.nextcloud.talk.models.json.chat;

import java.util.HashMap;

public class ChatUtils {

    public static String getParsedMessage(String message, HashMap<String, HashMap<String, String>> messageParameters) {
        if (messageParameters != null && messageParameters.size() > 0) {
            for (String key : messageParameters.keySet()) {
                HashMap<String, String> individualHashMap = messageParameters.get(key);
                if (individualHashMap.get("type").equals("user") || individualHashMap.get("type").equals("guest") || individualHashMap.get("type").equals("call")) {
                    message = message.replaceAll("\\{" + key + "\\}", "@" +
                            messageParameters.get(key).get("name"));
                } else if (individualHashMap.get("type").equals("file")) {
                    message = message.replaceAll("\\{" + key + "\\}", messageParameters.get(key).get("name"));
                }
            }
        }


        return message;
    }
}
