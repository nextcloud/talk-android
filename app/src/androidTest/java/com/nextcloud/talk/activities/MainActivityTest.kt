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

        val user = sut.userManager.createOrUpdateUser(
            "test",
            UserManager.UserAttributes(
                id = 0,
                serverUrl = "http://server/nc",
                currentUser = false,
                userId = "test",
                token = "test",
                displayName = null,
                pushConfigurationState = null,
                capabilities = null,
                certificateAlias = null,
                externalSignalingServer = null
            )
        ).blockingGet()

        assertNotNull("Error creating user", user)

        sut.runOnUiThread { sut.resetConversationsList() }
        println("User: " + user!!.id + " / " + user.userId + " / " + user.baseUrl)
    }
}
