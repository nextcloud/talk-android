/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.models.json.capabilities;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import lombok.Data;
import org.parceler.Parcel;

@Parcel
@Data
@JsonObject
class ThemingCapability {
    @JsonField(name = "name")
    String name;

    @JsonField(name = "url")
    String url;

    @JsonField(name = "slogan")
    String slogan;

    @JsonField(name = "color")
    String color;

    @JsonField(name = "color-text")
    String colorText;

    @JsonField(name = "color-element")
    String colorElement;

    @JsonField(name = "logo")
    String logo;

    @JsonField(name = "background")
    String background;

    @JsonField(name = "background-plain")
    boolean backgroundPlain;

    @JsonField(name = "background-default")
    boolean backgroundDefault;
}
