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

package com.moyn.talk.models;

import android.net.Uri;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.moyn.talk.models.json.converters.UriTypeConverter;

import org.parceler.Parcel;

import androidx.annotation.Nullable;

@Parcel
@JsonObject
public class RingtoneSettings {
    @JsonField(name = "ringtoneUri", typeConverter = UriTypeConverter.class)
    @Nullable
   public Uri ringtoneUri;
    @JsonField(name = "ringtoneName")
    public String ringtoneName;

    @Nullable
    public Uri getRingtoneUri() {
        return this.ringtoneUri;
    }

    public String getRingtoneName() {
        return this.ringtoneName;
    }

    public void setRingtoneUri(@Nullable Uri ringtoneUri) {
        this.ringtoneUri = ringtoneUri;
    }

    public void setRingtoneName(String ringtoneName) {
        this.ringtoneName = ringtoneName;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RingtoneSettings)) {
            return false;
        }
        final RingtoneSettings other = (RingtoneSettings) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$ringtoneUri = this.getRingtoneUri();
        final Object other$ringtoneUri = other.getRingtoneUri();
        if (this$ringtoneUri == null ? other$ringtoneUri != null : !this$ringtoneUri.equals(other$ringtoneUri)) {
            return false;
        }
        final Object this$ringtoneName = this.getRingtoneName();
        final Object other$ringtoneName = other.getRingtoneName();

        return this$ringtoneName == null ? other$ringtoneName == null : this$ringtoneName.equals(other$ringtoneName);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof RingtoneSettings;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $ringtoneUri = this.getRingtoneUri();
        result = result * PRIME + ($ringtoneUri == null ? 43 : $ringtoneUri.hashCode());
        final Object $ringtoneName = this.getRingtoneName();
        result = result * PRIME + ($ringtoneName == null ? 43 : $ringtoneName.hashCode());
        return result;
    }

    public String toString() {
        return "RingtoneSettings(ringtoneUri=" + this.getRingtoneUri() + ", ringtoneName=" + this.getRingtoneName() + ")";
    }
}
