/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Intent
import com.nextcloud.talk.chat.ChatActivity
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.conversationlist.ShareActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppTask

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class AppTasksTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun task(rootClassName: String, numActivities: Int = 1, taskId: Int = 1): ActivityManager.AppTask =
        ShadowAppTask.newInstance().also {
            shadowOf(it).setTaskInfo(
                ActivityManager.RecentTaskInfo().apply {
                    baseIntent = Intent().setClassName(context, rootClassName)
                    this.numActivities = numActivities
                    this.taskId = taskId
                }
            )
        }

    private val launcherRoot = AppTasks.LAUNCHER_ACTIVITY
    private val internalRoot = InternalEntryPoint.nameOf(ConversationsListActivity::class.java)

    @Test
    fun `a task only left in the recents is not the running task of the app`() {
        val leftInRecents = task(launcherRoot, numActivities = 0)
        val running = task(launcherRoot)

        assertSame(running, AppTasks.findRunningAppTask(context, listOf(leftInRecents, running)))
    }

    @Test
    fun `a task started through the internal entry point is a task of the app`() {
        val share = task(ShareActivity::class.java.name)
        val fromNotification = task(internalRoot)

        assertSame(fromNotification, AppTasks.findRunningAppTask(context, listOf(share, fromNotification)))
    }

    @Test
    fun `without a running task of the app there is none`() {
        val tasks = listOf(task(launcherRoot, numActivities = 0), task(ShareActivity::class.java.name))

        assertNull(AppTasks.findRunningAppTask(context, tasks))
    }

    @Test
    fun `the predicate leaves out a task`() {
        val own = task(launcherRoot, taskId = 1)
        val other = task(internalRoot, taskId = 2)

        assertSame(other, AppTasks.findRunningAppTask(context, listOf(own, other)) { it.taskId != 1 })
    }

    private val chatIntent = Intent().setClassName(context, ChatActivity::class.java.name)

    // the activity that opens the chat, e.g. the one a notification starts
    private val activity = Robolectric.buildActivity(Activity::class.java).get()

    @Test
    fun `a chat is opened on top of the screens of the running task of the app`() {
        AppTasks.openChatInAppTask(activity, chatIntent, listOf(task(launcherRoot)))

        val started = shadowOf(activity).nextStartedActivity
        assertEquals(ChatActivity::class.java.name, started.component?.className)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `without a running task of the app a chat is opened in a new task above the conversation list`() {
        AppTasks.openChatInAppTask(activity, chatIntent, listOf(task(launcherRoot, numActivities = 0)))

        val started = listOfNotNull(shadowOf(activity).nextStartedActivity, shadowOf(activity).nextStartedActivity)
        assertEquals(
            setOf(internalRoot, ChatActivity::class.java.name),
            started.map { it.component?.className }.toSet()
        )
    }
}
