/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Activity
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class LiveActivitiesTest {

    private val liveActivities = LiveActivities()

    private fun activityInTask(taskId: Int, isFinishing: Boolean = false): Activity =
        mock {
            on { this.taskId } doReturn taskId
            on { this.isFinishing } doReturn isFinishing
        }

    @Test
    fun `the activities of the app in the task are finished, not those of other tasks`() {
        val inTask = activityInTask(taskId = 7)
        val alsoInTask = activityInTask(taskId = 7)
        val inOtherTask = activityInTask(taskId = 8)
        listOf(inTask, alsoInTask, inOtherTask).forEach { liveActivities.onActivityCreated(it, null) }

        liveActivities.finishInTask(7)

        verify(inTask).finish()
        verify(alsoInTask).finish()
        verify(inOtherTask, never()).finish()
    }

    @Test
    fun `destroyed and finishing activities are not finished again`() {
        val destroyed = activityInTask(taskId = 7)
        val finishing = activityInTask(taskId = 7, isFinishing = true)
        liveActivities.onActivityCreated(destroyed, null)
        liveActivities.onActivityCreated(finishing, null)
        liveActivities.onActivityDestroyed(destroyed)

        liveActivities.finishInTask(7)

        verify(destroyed, never()).finish()
        verify(finishing, never()).finish()
    }
}
