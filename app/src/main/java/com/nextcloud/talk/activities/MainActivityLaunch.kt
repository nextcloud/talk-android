/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.content.Intent

/**
 * Decides how a newly created [MainActivity] handles its intent. MainActivity uses the standard launch mode, so a
 * launcher tap keeps the activities of a running task and an intent of another app can start it in that app's task.
 */
object MainActivityLaunch {

    enum class Decision {
        /** Route the intent to the conversation list, a chat or the login. */
        ROUTE,

        /** Launcher tap on a running task: finish, so the task shows the screen that was open. */
        SHOW_EXISTING_TASK,

        /** Started in the task of another app: start again in the app's own task. */
        MOVE_TO_OWN_TASK,

        /**
         * Started as task root by a VIEW intent: replace the task by one with the launcher intent as root. Android only
         * brings a task to front for an intent equal to its root intent, so the same VIEW intent would be dropped.
         */
        REPLACE_TASK_ROOT
    }

    fun decide(
        action: String?,
        categories: Set<String>?,
        flags: Int,
        isTaskRoot: Boolean,
        isStartedByOtherApp: Boolean
    ): Decision =
        when {
            isStartedByOtherApp && flags and Intent.FLAG_ACTIVITY_NEW_TASK == 0 -> Decision.MOVE_TO_OWN_TASK
            isLauncherIntent(action, categories) && !isTaskRoot -> Decision.SHOW_EXISTING_TASK
            action == Intent.ACTION_VIEW && isTaskRoot -> Decision.REPLACE_TASK_ROOT
            else -> Decision.ROUTE
        }

    private fun isLauncherIntent(action: String?, categories: Set<String>?): Boolean =
        action == Intent.ACTION_MAIN && categories?.contains(Intent.CATEGORY_LAUNCHER) == true
}
