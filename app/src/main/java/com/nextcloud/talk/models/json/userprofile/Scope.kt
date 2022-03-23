/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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
package com.nextcloud.talk.models.json.userprofile

enum class Scope(val id: String) {
    PRIVATE("v2-private"),
    LOCAL("v2-local"),
    FEDERATED("v2-federated"),
    PUBLISHED("v2-published")
}
