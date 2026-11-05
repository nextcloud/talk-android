/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.conversationlist.ShareActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAppTask

/**
 * Before Android 10, the base intent of a task that an alias started names the target of the alias, and only
 * origActivity names the alias.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [28])
class AppTasksBeforeAndroid10Test {

    private val context = RuntimeEnvironment.getApplication()

    @Suppress("DEPRECATION")
    private fun task(alias: String, taskId: Int): ActivityManager.AppTask =
        ShadowAppTask.newInstance().also {
            shadowOf(it).setTaskInfo(
                ActivityManager.RecentTaskInfo().apply {
                    baseIntent = Intent().setClassName(context, ConversationsListActivity::class.java.name)
                    origActivity = ComponentName(context, alias)
                    numActivities = 1
                    persistentId = taskId
                }
            )
        }

    @Test
    fun `a task the launcher started is the running task of the app`() {
        val launcherTask = task(AppTasks.LAUNCHER_ACTIVITY, taskId = 1)

        assertSame(launcherTask, AppTasks.findRunningAppTask(context, listOf(launcherTask)))
    }

    @Test
    fun `a task started through the internal entry point is a task of the app`() {
        val fromNotification = task(InternalEntryPoint.nameOf(ConversationsListActivity::class.java), taskId = 2)

        assertSame(fromNotification, AppTasks.findRunningAppTask(context, listOf(fromNotification)))
    }

    @Test
    fun `a task of a share is not a task of the app`() {
        val share = ShadowAppTask.newInstance().also {
            shadowOf(it).setTaskInfo(
                ActivityManager.RecentTaskInfo().apply {
                    baseIntent = Intent().setClassName(context, ShareActivity::class.java.name)
                    numActivities = 1
                }
            )
        }
        val launcherTask = task(AppTasks.LAUNCHER_ACTIVITY, taskId = 1)

        assertEquals(listOf(launcherTask), AppTasks.findAppTasks(context, listOf(share, launcherTask)) { true })
    }
}
