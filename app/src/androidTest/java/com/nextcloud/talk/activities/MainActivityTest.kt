package com.nextcloud.talk.activities

import androidx.test.espresso.intent.rule.IntentsTestRule
import com.nextcloud.talk.data.user.model.UserNgEntity
import org.junit.Assert.assertTrue
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
    suspend fun login() {
        val sut = activityRule.launchActivity(null)

        sut.usersRepository.insertUser(
            UserNgEntity(
                0,
                "test",
                "test",
                "http://server/nc",
                "test",
                null,
                null,
                null,
                null,
                null,
                false,
                scheduledForDeletion = false
            )
        )

        try {
            Thread.sleep(2000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        sut.runOnUiThread { sut.resetConversationsList() }

        assertTrue(sut.usersRepository.getUserWithUsernameAndServer("test", "http://server/nc") != null)

        try {
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
