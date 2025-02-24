/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Álvaro Brey <alvaro@alvarobrey.com>
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.database.user

import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.users.UserManager
import io.reactivex.Maybe
import io.reactivex.disposables.Disposable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Listens to changes in the database and provides the current user without needing to query the database everytime.
 */
@Singleton
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
