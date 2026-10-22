/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities

import android.content.Intent
import com.nextcloud.talk.activities.MainActivityLaunch.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityLaunchTest {

    private val launcherCategories = setOf(Intent.CATEGORY_LAUNCHER)
    private val launcherFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED

    @Test
    fun launcherTapOnRunningTaskShowsExistingTask() {
        val decision = MainActivityLaunch.decide(
            action = Intent.ACTION_MAIN,
            categories = launcherCategories,
            flags = launcherFlags,
            isTaskRoot = false,
            isStartedByOtherApp = true
        )

        assertEquals(Decision.SHOW_EXISTING_TASK, decision)
    }

    @Test
    fun launcherTapOnColdStartRoutes() {
        val decision = MainActivityLaunch.decide(
            action = Intent.ACTION_MAIN,
            categories = launcherCategories,
            flags = launcherFlags,
            isTaskRoot = true,
            isStartedByOtherApp = true
        )

        assertEquals(Decision.ROUTE, decision)
    }

    @Test
    fun launcherIntentWithClearTaskFromInsideTheAppRoutes() {
        val decision = MainActivityLaunch.decide(
            action = Intent.ACTION_MAIN,
            categories = launcherCategories,
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            isTaskRoot = true,
            isStartedByOtherApp = false
        )

        assertEquals(Decision.ROUTE, decision)
    }

    @Test
    fun deepLinkOfOtherAppWithoutNewTaskMovesToOwnTask() {
        val decision = MainActivityLaunch.decide(
            action = Intent.ACTION_VIEW,
            categories = setOf(Intent.CATEGORY_BROWSABLE),
            flags = 0,
            isTaskRoot = false,
            isStartedByOtherApp = true
        )

        assertEquals(Decision.MOVE_TO_OWN_TASK, decision)
    }

    @Test
    fun launcherIntentOfOtherAppWithoutNewTaskMovesToOwnTask() {
        val decision = MainActivityLaunch.decide(
            action = Intent.ACTION_MAIN,
            categories = launcherCategories,
            flags = 0,
            isTaskRoot = false,
            isStartedByOtherApp = true
        )

        assertEquals(Decision.MOVE_TO_OWN_TASK, decision)
    }

    @Test
    fun deepLinkOfOtherAppWithNewTaskRoutes() {
        val decision = MainActivityLaunch.decide(
            action = Intent.ACTION_VIEW,
            categories = setOf(Intent.CATEGORY_BROWSABLE),
            flags = Intent.FLAG_ACTIVITY_NEW_TASK,
            isTaskRoot = false,
            isStartedByOtherApp = true
        )

        assertEquals(Decision.ROUTE, decision)
    }

    @Test
    fun deepLinkStartingNewTaskReplacesTaskRoot() {
        val decision = MainActivityLaunch.decide(
            action = Intent.ACTION_VIEW,
            categories = setOf(Intent.CATEGORY_BROWSABLE),
            flags = Intent.FLAG_ACTIVITY_NEW_TASK,
            isTaskRoot = true,
            isStartedByOtherApp = true
        )

        assertEquals(Decision.REPLACE_TASK_ROOT, decision)
    }

    @Test
    fun shortcutStartingNewTaskReplacesTaskRoot() {
        val decision = MainActivityLaunch.decide(
            action = Intent.ACTION_VIEW,
            categories = null,
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            isTaskRoot = true,
            isStartedByOtherApp = true
        )

        assertEquals(Decision.REPLACE_TASK_ROOT, decision)
    }

    @Test
    fun notificationIntentRoutes() {
        val decision = MainActivityLaunch.decide(
            action = null,
            categories = null,
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
            isTaskRoot = true,
            isStartedByOtherApp = true
        )

        assertEquals(Decision.ROUTE, decision)
    }

    @Test
    fun startFromInsideTheAppWithoutFlagsRoutes() {
        val decision = MainActivityLaunch.decide(
            action = null,
            categories = null,
            flags = 0,
            isTaskRoot = false,
            isStartedByOtherApp = false
        )

        assertEquals(Decision.ROUTE, decision)
    }
}
