/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Álvaro Brey
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
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

package com.nextcloud.talk.shareditems.model

import java.util.Locale

enum class SharedItemType {

    AUDIO,
    FILE,
    MEDIA,
    RECORDING,
    VOICE,
    LOCATION,
    DECKCARD,
    OTHER,
    POLL;

    companion object {
        fun typeFor(name: String) = valueOf(name.uppercase(Locale.ROOT))
    }
}
