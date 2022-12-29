package com.nextcloud.talk.activities

import android.util.Log
import androidx.test.espresso.intent.rule.IntentsTestRule
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.utils.ApiUtils
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.junit.Rule
import org.junit.Test

class MainActivityIT {
    @get:Rule
    val activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(
        MainActivity::class.java,
        true,
        false
    )

    @Test
    fun login() {
        val sut = activityRule.launchActivity(null)

        val baseUrl = "http://10.0.2.2/nc"
        val userId = "test"
        val token = "test"
        val credentials = ApiUtils.getCredentials(userId, token)
        var capabilities: Capabilities?

        sut.ncApi.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { capabilitiesOverall: CapabilitiesOverall? ->
                capabilities = capabilitiesOverall?.ocs?.data?.capabilities

                sut.userUtils.createOrUpdateUser(
                    userId,
                    token,
                    baseUrl,
                    "test",
                    null,
                    true,
                    userId,
                    null,
                    LoganSquare.serialize<Capabilities>(capabilities),
                    null,
                    null
                )
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { userEntity: UserEntity? -> Log.i("test", "stored: " + userEntity.toString()) }
                try {
                    Thread.sleep(2000)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }

                sut.runOnUiThread { sut.resetConversationsList() }
            }

        try {
            Thread.sleep(20000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
