/*
 * Nextcloud Talk application
 *
 * @author Julius Linus
 * Copyright (C) 2023 Julius Linus <julius.linus@nextcloud.com>
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
package com.nextcloud.talk.translate.repositories.model

import android.os.Parcelable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.nextcloud.talk.models.json.generic.GenericMeta
import kotlinx.parcelize.Parcelize

@Parcelize
@Serializable
data class TranslateOCS(
    var meta: GenericMeta?,
    var data: TranslateData?
) : Parcelable {
    // This constructor is added to work with the 'com.bluelinelabs.logansquare.annotation.JsonObject'
    constructor() : this(null, TranslateData())
}
