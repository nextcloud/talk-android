/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.models.json.signaling;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.nextcloud.talk.models.json.converters.ObjectParcelConverter;
import java.util.HashMap;
import lombok.Data;
import org.parceler.ParcelPropertyConverter;

@Data
@JsonObject
public class DataChannelMessageNick {
    @JsonField(name = "type")
    String type;

    @ParcelPropertyConverter(ObjectParcelConverter.class)
    @JsonField(name = "payload")
    HashMap<String, String> payload;

    public DataChannelMessageNick(String type) {
        this.type = type;
    }

    public DataChannelMessageNick() {
    }
}
