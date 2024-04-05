/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2021 Nextcloud GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import androidx.test.espresso.intent.rule.IntentsTestRule
import com.nextcloud.talk.users.UserManager
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(
        MainActivity::class.java,
        true,
        false
    )

    @Test
    fun login() {
        val sut = activityRule.launchActivity(null)

        val user = sut.userManager.storeProfile(
            "test",
            UserManager.UserAttributes(
                null,
                serverUrl = "http://server/nc",
                currentUser = true,
                userId = "test",
                token = "test",
                displayName = "Test Name",
                pushConfigurationState = null,
                capabilities = null,
                serverVersion = null,
                certificateAlias = null,
                externalSignalingServer = null
            )
        ).blockingGet()

        assertNotNull("Error creating user", user)
    }
}
