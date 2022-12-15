/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.utils.database.user

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import io.reactivex.Maybe
import io.reactivex.disposables.Disposable
import javax.inject.Inject

/**
 * Listens to changes in the database and provides the current user without needing to query the database everytime.
 */
class CurrentUserProviderImpl @Inject constructor(private val userManager: UserManager) : CurrentUserProviderNew {

    private var _currentUser: User? = null

    // synchronized to avoid multiple observers initialized from different threads
    @get:Synchronized
    @set:Synchronized
    private var currentUserObserver: Disposable? = null

    override val currentUser: Maybe<User>
        get() {
            if (_currentUser == null) {
                // immediately get a result synchronously
                _currentUser = userManager.currentUser.blockingGet()
                if (currentUserObserver == null) {
                    currentUserObserver = userManager.currentUserObservable
                        .subscribe {
                            _currentUser = it
                        }
                }
            }
            return _currentUser?.let { Maybe.just(it) } ?: Maybe.empty()
        }
}
