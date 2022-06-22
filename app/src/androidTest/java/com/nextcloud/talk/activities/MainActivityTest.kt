package com.nextcloud.talk.activities

import androidx.test.espresso.intent.rule.IntentsTestRule
import com.nextcloud.talk.data.user.model.UserNgEntity
import com.nextcloud.talk.users.UserManager
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import org.junit.Assert.fail
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

        sut.userManager.createOrUpdateUser(
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
        ).subscribe(object : Observer<UserNgEntity?> {
            override fun onSubscribe(d: Disposable) {
                // unused atm
            }

            override fun onNext(user: UserNgEntity) {
                sut.runOnUiThread { sut.resetConversationsList() }

                println("User: " + user.id + " / " + user.userId + " / " + user.baseUrl)
            }

            override fun onError(e: Throwable) {
                fail("No user created")
            }

            override fun onComplete() {
                // unused atm
            }
        })
    }
}
