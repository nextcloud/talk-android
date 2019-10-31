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

package com.nextcloud.talk.models.json.push

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.android.parcel.Parcelize
import lombok.Data
import org.parceler.Parcel

@Parcel
@Data
@JsonObject
@Parcelize
class PushConfigurationState(
  @JsonField(name = ["pushToken"])
  var pushToken: String? = null,
  @JsonField(name = ["deviceIdentifier"])
  var deviceIdentifier: String? = null,
  @JsonField(name = ["deviceIdentifierSignature"])
  var deviceIdentifierSignature: String? = null,
  @JsonField(name = ["userPublicKey"])
  var userPublicKey: String? = null,
  @JsonField(name = ["usesRegularPass"])
  var usesRegularPass: Boolean = false
): Parcelable