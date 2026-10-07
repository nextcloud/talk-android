/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingStopCoordinatorTest {

    private class FakeScheduler : DelayScheduler {
        var pending: Runnable? = null
        var delay = -1L

        override fun postDelayed(runnable: Runnable, delayMs: Long) {
            pending = runnable
            delay = delayMs
        }

        override fun cancel(runnable: Runnable) {
            if (pending === runnable) pending = null
        }

        /** The window ends. */
        fun fire() {
            pending?.also { pending = null }?.run()
        }
    }

    private val scheduler = FakeScheduler()
    private var stops = 0
    private val coordinator = RecordingStopCoordinator(scheduler) { stops++ }

    @Test
    fun stopOutsideTheWindowIsIssuedAtOnce() {
        assertTrue(coordinator.requestStop(StopAction.SEND))
        assertEquals(1, stops)
    }

    @Test
    fun stopInsideTheWindowWaitsAndIsIssuedWhenItEnds() {
        assertTrue(coordinator.beginSwitch())
        assertEquals(RecordingStopCoordinator.SWITCH_SETTLE_MS, scheduler.delay)

        assertTrue(coordinator.requestStop(StopAction.SEND))
        assertEquals(0, stops)

        scheduler.fire()

        assertEquals(1, stops)
        assertFalse(coordinator.settling)
    }

    @Test
    fun releaseInsideTheWindowDoesNotStopBeforeItEnds() {
        coordinator.beginSwitch()

        // what release() does: cancel the recording, and the camera stays bound while the window is open
        coordinator.requestStop(StopAction.DISCARD)
        assertEquals(0, stops)
        assertTrue(coordinator.settling)

        scheduler.fire()

        assertEquals(1, stops)
        assertEquals(StopAction.DISCARD, coordinator.action)
    }

    @Test
    fun noStopIsIssuedWhileTheWindowIsOpenWhateverIsRequested() {
        coordinator.beginSwitch()
        coordinator.requestStop(StopAction.DISCARD)
        coordinator.requestStop(StopAction.SEND)
        coordinator.requestStop(StopAction.DISCARD)
        assertEquals(0, stops)
    }

    @Test
    fun secondSwitchInsideTheWindowIsRefused() {
        assertTrue(coordinator.beginSwitch())
        assertFalse(coordinator.beginSwitch())

        scheduler.fire()

        assertTrue(coordinator.beginSwitch())
    }

    @Test
    fun switchAfterAStopRequestIsRefused() {
        coordinator.requestStop(StopAction.SEND)
        assertFalse(coordinator.beginSwitch())
    }

    @Test
    fun sendAfterCancelDoesNotSendTheCancelledVideo() {
        assertTrue(coordinator.requestStop(StopAction.DISCARD))
        assertFalse(coordinator.requestStop(StopAction.SEND))

        assertEquals(StopAction.DISCARD, coordinator.action)
        assertEquals(1, stops)
    }

    @Test
    fun sendAfterCancelInsideTheWindowStaysCancelled() {
        coordinator.beginSwitch()
        coordinator.requestStop(StopAction.DISCARD)
        coordinator.requestStop(StopAction.SEND)

        scheduler.fire()

        assertEquals(StopAction.DISCARD, coordinator.action)
        assertEquals(1, stops)
    }

    @Test
    fun cancelAfterSendDoesNotDiscardTheVideoBeingSent() {
        coordinator.requestStop(StopAction.SEND)
        assertFalse(coordinator.requestStop(StopAction.DISCARD))
        assertEquals(StopAction.SEND, coordinator.action)
    }

    @Test
    fun resetClosesTheWindowAndForgetsTheRequest() {
        coordinator.beginSwitch()
        coordinator.requestStop(StopAction.SEND)

        coordinator.reset()
        scheduler.fire()

        assertEquals(0, stops)
        assertNull(coordinator.action)
        assertFalse(coordinator.settling)
        assertTrue(coordinator.requestStop(StopAction.SEND))
    }
}
