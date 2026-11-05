/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle

/**
 * The activities of the app that are created and not destroyed yet. Unlike [Activity.finishAffinity], which also
 * finishes the activities of another app below that have the same task affinity, it can finish only those of the app
 * in a task, e.g. one of another app that started a screen of the app in it.
 */
class LiveActivities : Application.ActivityLifecycleCallbacks {

    private val activities = LinkedHashSet<Activity>()

    /**
     * Finishes the activities of the app in the task [taskId]. Only the ones in memory: those Android didn't create
     * again yet after the process was killed are left.
     */
    fun finishInTask(taskId: Int) {
        activities.filter { it.taskId == taskId && !it.isFinishing }.forEach(Activity::finish)
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        activities.add(activity)
    }

    override fun onActivityDestroyed(activity: Activity) {
        activities.remove(activity)
    }

    override fun onActivityStarted(activity: Activity) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
}
