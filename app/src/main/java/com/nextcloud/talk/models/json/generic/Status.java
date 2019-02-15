/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

package com.nextcloud.talk.models.json.generic;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import lombok.Data;
import org.parceler.Parcel;

@Parcel
@Data
@JsonObject
public class Status {
    @JsonField(name = "installed")
    public boolean installed;

    @JsonField(name = "maintenance")
    public boolean maintenance;

    @JsonField(name = "upgrade")
    public boolean needsUpgrade;

    @JsonField(name = "version")
    public String version;

    @JsonField(name = "versionstring")
    public String versionString;

    @JsonField(name = "edition")
    public String edition;

    @JsonField(name = "productname")
    public String productName;
}
