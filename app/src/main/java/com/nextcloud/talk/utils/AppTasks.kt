/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.conversationlist.ConversationsListActivity
import com.nextcloud.talk.conversationlist.ShareActivity
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.launch

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

/** Removes this task, unless it was removed meanwhile. */
fun ActivityManager.AppTask.finishAndRemoveTaskIfPresent() {
    try {
        finishAndRemoveTask()
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Task was removed before it was finished", e)
    }
}

/** Moves this task to the front and returns true, or returns false if the task was removed meanwhile. */
fun ActivityManager.AppTask.moveToFrontIfPresent(): Boolean =
    try {
        moveToFront()
        true
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Task was removed before it was moved to the front", e)
        false
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

    /**
     * The launcher activity, an alias of the conversation list, by its name in the manifest, which home-screen icons
     * and pinned shortcuts of earlier versions refer to.
     */
    const val LAUNCHER_ACTIVITY = "com.nextcloud.talk.activities.MainActivity"

    /** Creates the intent the launcher starts the app with, in a new task or the running one of the app. */
    fun createLauncherIntent(context: Context): Intent =
        Intent()
            .setClassName(context, LAUNCHER_ACTIVITY)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Starts the app anew in a fresh task, e.g. after the account or conversation shown was removed. */
    fun createRestartIntent(context: Context): Intent =
        createLauncherIntent(context).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)

    /**
     * Starts the app anew in a fresh task with the conversation list of the account [userId], e.g. from a call whose
     * account isn't the default one, when no task of the app is left to show it in.
     */
    fun createRestartIntent(context: Context, userId: Long): Intent =
        ConversationsListActivity.createAccountSwitchIntent(context, userId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

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
     * The tasks of the app among [appTasks] whose info matches [predicate]: those rooted at the conversation list,
     * started from the launcher or through its internal entry point, unlike the tasks of a share (see ShareActivity),
     * of a call or one another app started the list in. A task removed meanwhile is left out.
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

    /**
     * Whether the task [taskId] is one of [findRunningAppTask], e.g. not a task of another app that started the
     * conversation list in it.
     */
    fun isRunningAppTask(context: Context, taskId: Int): Boolean =
        findRunningAppTask(context) { it.taskIdCompat == taskId } != null

    /**
     * The tasks among [appTasks] that are only left in the recents, without activities: those of [findAppTasks],
     * and those another app started the conversation list in.
     */
    fun findTasksLeftInRecents(
        context: Context,
        appTasks: List<ActivityManager.AppTask> = appTasksOf(context)
    ): List<ActivityManager.AppTask> =
        tasksRootedAt(appTasks, appTaskRoots() + ConversationsListActivity::class.java.name) { it.numActivities == 0 }

    /**
     * Removes the tasks of the app that are only left in the recents, see [findTasksLeftInRecents], when a screen
     * that can start a task of the app is created: the conversation list, or the top of a new back stack, see
     * [ConversationsListActivity.createBackStack]. Without a task affinity, the recents only replace a task by one
     * with the same root intent, so e.g. a task a deep link started would stay there next to the one a notification
     * starts. In the scope of the app, as it asks ActivityManager for every task while the screen is created.
     */
    fun removeTasksLeftInRecents(context: Context) {
        val applicationContext = context.applicationContext
        NextcloudTalkApplication.sharedApplication!!.appScope.launch {
            findTasksLeftInRecents(applicationContext).forEach { it.finishAndRemoveTaskIfPresent() }
        }
    }

    /** Whether [intent] relaunches a task from the recents: the intent it was started with, taken already. */
    fun isRelaunchedFromRecents(intent: Intent): Boolean =
        intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0

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
            info != null && info.rootActivity?.className in roots && predicate(info)
        }
}

/**
 * The activity that the root of this task was started as. Before Android 10, the base intent of a task that an alias
 * started names the target of the alias instead, while origActivity names the alias on every version.
 *
 * Lint takes origActivity for the field of TaskInfo, added in API 29. RecentTaskInfo declares it itself since API 1
 * and only inherits it since API 29, and the bytecode reads it from RecentTaskInfo, so it exists on every version.
 */
@get:SuppressLint("NewApi")
private val ActivityManager.RecentTaskInfo.rootActivity: ComponentName?
    get() = origActivity ?: baseIntent.component

/** Whether this is the task of a share, which ShareActivity started in a task of its own. */
val ActivityManager.RecentTaskInfo.isShareTask: Boolean
    get() = rootActivity?.className == ShareActivity::class.java.name

// TaskInfo.taskId only exists since API 29; before that persistentId is the task id
@Suppress("DEPRECATION")
val ActivityManager.RecentTaskInfo.taskIdCompat: Int
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) taskId else persistentId
