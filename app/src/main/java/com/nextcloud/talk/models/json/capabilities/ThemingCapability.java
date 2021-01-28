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

@Parcel
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

    public ThemingCapability() {
    }

    public String getName() {
        return this.name;
    }

    public String getUrl() {
        return this.url;
    }

    public String getSlogan() {
        return this.slogan;
    }

    public String getColor() {
        return this.color;
    }

    public String getColorText() {
        return this.colorText;
    }

    public String getColorElement() {
        return this.colorElement;
    }

    public String getLogo() {
        return this.logo;
    }

    public String getBackground() {
        return this.background;
    }

    public boolean isBackgroundPlain() {
        return this.backgroundPlain;
    }

    public boolean isBackgroundDefault() {
        return this.backgroundDefault;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setSlogan(String slogan) {
        this.slogan = slogan;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setColorText(String colorText) {
        this.colorText = colorText;
    }

    public void setColorElement(String colorElement) {
        this.colorElement = colorElement;
    }

    public void setLogo(String logo) {
        this.logo = logo;
    }

    public void setBackground(String background) {
        this.background = background;
    }

    public void setBackgroundPlain(boolean backgroundPlain) {
        this.backgroundPlain = backgroundPlain;
    }

    public void setBackgroundDefault(boolean backgroundDefault) {
        this.backgroundDefault = backgroundDefault;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof ThemingCapability)) return false;
        final ThemingCapability other = (ThemingCapability) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$name = this.getName();
        final Object other$name = other.getName();
        if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
        final Object this$url = this.getUrl();
        final Object other$url = other.getUrl();
        if (this$url == null ? other$url != null : !this$url.equals(other$url)) return false;
        final Object this$slogan = this.getSlogan();
        final Object other$slogan = other.getSlogan();
        if (this$slogan == null ? other$slogan != null : !this$slogan.equals(other$slogan)) return false;
        final Object this$color = this.getColor();
        final Object other$color = other.getColor();
        if (this$color == null ? other$color != null : !this$color.equals(other$color)) return false;
        final Object this$colorText = this.getColorText();
        final Object other$colorText = other.getColorText();
        if (this$colorText == null ? other$colorText != null : !this$colorText.equals(other$colorText)) return false;
        final Object this$colorElement = this.getColorElement();
        final Object other$colorElement = other.getColorElement();
        if (this$colorElement == null ? other$colorElement != null : !this$colorElement.equals(other$colorElement))
            return false;
        final Object this$logo = this.getLogo();
        final Object other$logo = other.getLogo();
        if (this$logo == null ? other$logo != null : !this$logo.equals(other$logo)) return false;
        final Object this$background = this.getBackground();
        final Object other$background = other.getBackground();
        if (this$background == null ? other$background != null : !this$background.equals(other$background))
            return false;
        if (this.isBackgroundPlain() != other.isBackgroundPlain()) return false;
        if (this.isBackgroundDefault() != other.isBackgroundDefault()) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ThemingCapability;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $name = this.getName();
        result = result * PRIME + ($name == null ? 43 : $name.hashCode());
        final Object $url = this.getUrl();
        result = result * PRIME + ($url == null ? 43 : $url.hashCode());
        final Object $slogan = this.getSlogan();
        result = result * PRIME + ($slogan == null ? 43 : $slogan.hashCode());
        final Object $color = this.getColor();
        result = result * PRIME + ($color == null ? 43 : $color.hashCode());
        final Object $colorText = this.getColorText();
        result = result * PRIME + ($colorText == null ? 43 : $colorText.hashCode());
        final Object $colorElement = this.getColorElement();
        result = result * PRIME + ($colorElement == null ? 43 : $colorElement.hashCode());
        final Object $logo = this.getLogo();
        result = result * PRIME + ($logo == null ? 43 : $logo.hashCode());
        final Object $background = this.getBackground();
        result = result * PRIME + ($background == null ? 43 : $background.hashCode());
        result = result * PRIME + (this.isBackgroundPlain() ? 79 : 97);
        result = result * PRIME + (this.isBackgroundDefault() ? 79 : 97);
        return result;
    }

    public String toString() {
        return "ThemingCapability(name=" + this.getName() + ", url=" + this.getUrl() + ", slogan=" + this.getSlogan() + ", color=" + this.getColor() + ", colorText=" + this.getColorText() + ", colorElement=" + this.getColorElement() + ", logo=" + this.getLogo() + ", background=" + this.getBackground() + ", backgroundPlain=" + this.isBackgroundPlain() + ", backgroundDefault=" + this.isBackgroundDefault() + ")";
    }
}
