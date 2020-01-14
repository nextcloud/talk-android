/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.nextcloud.talk.newarch.features.account.loginentry

import kotlinx.serialization.Serializable

enum class LoginEntryState {
    PENDING_CHECK,
    CHECKING,
    FAILED,
    OK
}

enum class LoginEntryStateClarification {
    INVALID_PARSED_DATA,
    PROFILE_FETCH_FAILED,
    CAPABILITIES_FETCH_FAILED,
    SIGNALING_SETTINGS_FETCH_FAILED,
    PUSH_REGISTRATION_MISSING_TOKEN,
    PUSH_REGISTRATION_WITH_SERVER_FAILED,
    PUSH_REGISTRATION_WITH_PUSH_PROXY_FAILED,
    ACCOUNT_UPDATED,
    ACCOUNT_CREATED
}

@Serializable
data class LoginEntryStateWrapper(val state: LoginEntryState, val clarification: LoginEntryStateClarification?)