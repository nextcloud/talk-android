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

import org.parceler.Parcel;

import java.util.Objects;

import lombok.Data;

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThemingCapability)) return false;
        ThemingCapability that = (ThemingCapability) o;
        return backgroundPlain == that.backgroundPlain &&
                backgroundDefault == that.backgroundDefault &&
                Objects.equals(name, that.name) &&
                Objects.equals(url, that.url) &&
                Objects.equals(slogan, that.slogan) &&
                Objects.equals(color, that.color) &&
                Objects.equals(colorText, that.colorText) &&
                Objects.equals(colorElement, that.colorElement) &&
                Objects.equals(logo, that.logo) &&
                Objects.equals(background, that.background);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, url, slogan, color, colorText, colorElement, logo, background, backgroundPlain, backgroundDefault);
    }
}
