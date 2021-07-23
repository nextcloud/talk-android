package com.nextcloud.talk.activities

import android.util.Log
import androidx.test.espresso.intent.rule.IntentsTestRule
import com.nextcloud.talk.models.database.UserEntity
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import junit.framework.Assert.assertTrue
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
        sut.userUtils.createOrUpdateUser(
            "test",
            "test",
            "http://10.0.2.2/nc",
            "test",
            null,
            true,
            "test",
            null,
            null,
            null,
            null
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { userEntity: UserEntity? -> Log.i("test", "stored: " + userEntity.toString()) },
                { throwable: Throwable? -> Log.e("test", "throwable") },
                { Log.d("test", "complete") }
            )

        try {
            Thread.sleep(2000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        sut.runOnUiThread { sut.resetConversationsList() }

        assertTrue(sut.userUtils.getIfUserWithUsernameAndServer("test", "http://10.0.2.2/nc"))

        try {
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
