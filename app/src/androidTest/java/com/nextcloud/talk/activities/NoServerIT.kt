package com.nextcloud.talk.activities

import android.util.Log
import android.view.View
import androidx.test.espresso.intent.rule.IntentsTestRule
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.adapters.items.ConversationItem
import com.nextcloud.talk.controllers.ConversationsListController
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.capabilities.Capabilities
import com.nextcloud.talk.models.json.capabilities.SpreedCapability
import com.nextcloud.talk.models.json.conversations.Conversation
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.junit.Rule
import org.junit.Test

class NoServerIT {
    @get:Rule
    val activityRule: IntentsTestRule<MainActivity> = IntentsTestRule(
        MainActivity::class.java,
        true,
        false
    )

    @Test
    fun showConversationList() {
        val sut = activityRule.launchActivity(null)

        val baseUrl = "http://server.com"
        val userId = "test"
        val token = "test"
        val capabilities = Capabilities().apply {
            spreedCapability = SpreedCapability()
            spreedCapability.features = arrayListOf()
            spreedCapability.features.add(0, "no-ping")
            spreedCapability.features.add(1, "conversation-v4")
        }

        sut.userUtils.createOrUpdateUser(
            userId,
            token,
            baseUrl,
            "test",
            null,
            true,
            userId,
            null,
            LoganSquare.serialize(capabilities),
            null,
            null
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { userEntity: UserEntity? -> Log.i("test", "stored: " + userEntity.toString()) }

        sut.runOnUiThread {
            sut.resetConversationsList()
        }

        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        val controller =
            sut.router?.getControllerWithTag("ConversationListController") as ConversationsListController

        val conversation = Conversation().apply {
            displayName = "Test Conversation"
            this.token = "1"
            type = Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL
        }

        sut.runOnUiThread {
            controller.loadingContent.visibility = View.GONE
            controller.emptyLayoutView.visibility = View.GONE
            controller.swipeRefreshLayout.visibility = View.VISIBLE
            controller.recyclerView.visibility = View.VISIBLE
            controller.callItems.add(ConversationItem(conversation, sut.userUtils.currentUser, sut))
            controller.adapter.updateDataSet(controller.callItems, false)
        }

        try {
            Thread.sleep(5000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    @Test
    fun showSettings() {
        val sut = activityRule.launchActivity(null)

        val baseUrl = "http://10.0.2.2/nc"
        val userId = "test"
        val token = "test"
        val capabilities = Capabilities().apply {
            spreedCapability = SpreedCapability()
            spreedCapability.features = arrayListOf()
            spreedCapability.features.add(0, "no-ping")
            spreedCapability.features.add(1, "conversation-v4")
        }

        sut.userUtils.createOrUpdateUser(
            userId,
            token,
            baseUrl,
            "test",
            null,
            true,
            userId,
            null,
            LoganSquare.serialize(capabilities),
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

        sut.runOnUiThread { sut.openSettings() }

        try {
            Thread.sleep(20000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    fun shortWait() {
        try {
            Thread.sleep(20000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }
}
