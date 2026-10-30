/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.util.Log
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.utils.bundle.BundleKeys

// A task listed by ActivityManager.getAppTasks can be removed before it is used, e.g. when it is swiped from the
// recents or finished by an activity of another task, and the calls on it throw an IllegalArgumentException then.

private const val TAG = "AppTasks"

/** The info of this task, or null if it was removed meanwhile. */
fun ActivityManager.AppTask.taskInfoOrNull(): ActivityManager.RecentTaskInfo? =
    try {
        taskInfo
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Task was removed before its info was read", e)
        null
    }

/** Starts [intent] on top of this task and returns true, or returns false if the task was removed meanwhile. */
fun ActivityManager.AppTask.startActivityIfPresent(context: Context, intent: Intent): Boolean =
    try {
        startActivity(context, intent, null)
        true
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Task was removed before the activity was started in it", e)
        false
    }

/** The task of the app that the launcher starts, and the screens opened in it from outside the app. */
object AppTasks {

    /** The launcher activity, by its name in the manifest, which home-screen icons and pinned shortcuts refer to. */
    const val LAUNCHER_ACTIVITY = "com.nextcloud.talk.activities.MainActivity"

    /**
     * Opens [chatIntent] on top of the screens open in the running task of the app, or else in a new task with the
     * conversation list of its account below it, which the launcher and the internal entry point find again, unlike a
     * task rooted at the chat. Only the chat is created, and the list when going back.
     */
    fun openChatInAppTask(
        context: Context,
        chatIntent: Intent,
        appTasks: List<ActivityManager.AppTask> = appTasksOf(context)
    ) {
        val isStartedInAppTask =
            findRunningAppTask(context, appTasks)?.startActivityIfPresent(context, chatIntent) == true
        if (!isStartedInAppTask) {
            val userId = chatIntent.getLongExtra(BundleKeys.KEY_INTERNAL_USER_ID, 0L)
            val listIntent = ConversationsListActivity.createAccountSwitchIntent(context, userId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivities(arrayOf(listIntent, chatIntent))
        }
    }

    /**
     * The tasks of the app among [appTasks] whose info matches [predicate]: those rooted at the launcher activity or
     * at the internal entry point of the conversation list, unlike the tasks of a share (see ShareActivity), of a call
     * or one another app started the list in. A task removed meanwhile is left out.
     */
    fun findAppTasks(
        context: Context,
        appTasks: List<ActivityManager.AppTask> = appTasksOf(context),
        predicate: (ActivityManager.RecentTaskInfo) -> Boolean
    ): List<ActivityManager.AppTask> = tasksRootedAt(appTasks, appTaskRoots(), predicate)

    /**
     * The running task of the app among [appTasks] whose info matches [predicate]: one of [findAppTasks] with
     * activities, unlike a task that is only left in the recents, which starting a screen in would bring back.
     */
    fun findRunningAppTask(
        context: Context,
        appTasks: List<ActivityManager.AppTask> = appTasksOf(context),
        predicate: (ActivityManager.RecentTaskInfo) -> Boolean = { true }
    ): ActivityManager.AppTask? =
        findAppTasks(context, appTasks) { it.numActivities > 0 && predicate(it) }.firstOrNull()

    private fun appTaskRoots(): Set<String> =
        setOf(LAUNCHER_ACTIVITY, InternalEntryPoint.nameOf(ConversationsListActivity::class.java))

    private fun appTasksOf(context: Context): List<ActivityManager.AppTask> =
        context.getSystemService(ActivityManager::class.java).appTasks

    /**
     * The tasks among [appTasks] whose root is one of the activities named [roots] and whose info matches
     * [predicate]. A task removed meanwhile is left out.
     */
    private fun tasksRootedAt(
        appTasks: List<ActivityManager.AppTask>,
        roots: Set<String>,
        predicate: (ActivityManager.RecentTaskInfo) -> Boolean
    ): List<ActivityManager.AppTask> =
        appTasks.filter { task ->
            val info = task.taskInfoOrNull()
            info != null && info.baseIntent.component?.className in roots && predicate(info)
        }
}
