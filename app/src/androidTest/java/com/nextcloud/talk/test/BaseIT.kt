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

package com.nextcloud.talk.test

import android.annotation.SuppressLint
import androidx.test.platform.app.InstrumentationRegistry
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.data.source.local.TalkDatabase
import com.nextcloud.talk.data.user.UsersRepositoryImpl
import com.nextcloud.talk.users.UserManager
import org.junit.Before

/**
 * Base class for integration tests that need to ensure database consistency
 */
abstract class BaseIT {

    private lateinit var database: TalkDatabase
    private lateinit var userManager: UserManager

    @SuppressLint("CheckResult")
    @Before
    fun setUp() {
        initFields()

        database.clearAllTables()

        createTestUser()
    }

    fun initFields() {
        // these should be injected but AutoDagger does not work here
        database = TalkDatabase.getInstance(NextcloudTalkApplication.sharedApplication!!)
        userManager = UserManager(UsersRepositoryImpl(database.usersDao()))
    }

    @SuppressLint("CheckResult")
    private fun createTestUser() {
        val arguments = InstrumentationRegistry.getArguments()

        val baseUrl = arguments.getString(ARG_SERVER_URL)
        val loginName = arguments.getString(ARG_SERVER_USERNAME)
        val password = arguments.getString(ARG_SERVER_PASSWORD)

        userManager.createOrUpdateUser(
            loginName,
            UserManager.UserAttributes(
                id = 1, serverUrl = baseUrl,
                currentUser = true,
                userId = loginName, token = password, displayName = loginName, pushConfigurationState = null,
                capabilities = null, certificateAlias = null, externalSignalingServer = null
            )
        ).blockingGet()
    }

    companion object {
        private const val ARG_SERVER_URL = "TEST_SERVER_URL"
        private const val ARG_SERVER_USERNAME = "TEST_SERVER_USERNAME"
        private const val ARG_SERVER_PASSWORD = "TEST_SERVER_USERNAME"
    }
}
