/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ThrottleLatestTest {

    @Test
    fun `an isolated value is passed on without waiting for the window`() =
        runTest {
            val emissions = mutableListOf<Pair<Long, Int>>()

            flow {
                emit(1)
                delay(SPACED_APART)
                emit(2)
            }.throttleLatest(WINDOW).collect { emissions.add(testScheduler.currentTime to it) }

            assertEquals(listOf(0L to 1, SPACED_APART to 2), emissions)
        }

    @Test
    fun `values within one window collapse into the most recent one`() =
        runTest {
            val emissions = mutableListOf<Pair<Long, Int>>()

            flow {
                emit(1)
                delay(WITHIN_WINDOW)
                emit(2)
                delay(WITHIN_WINDOW)
                emit(3)
            }.throttleLatest(WINDOW).collect { emissions.add(testScheduler.currentTime to it) }

            assertEquals(listOf(0L to 1, WINDOW to 3), emissions)
        }

    @Test
    fun `the last value is emitted even when the upstream completes inside the window`() =
        runTest {
            val emissions = mutableListOf<Pair<Long, Int>>()

            flow {
                emit(1)
                delay(WITHIN_WINDOW)
                emit(2)
            }.throttleLatest(WINDOW).collect { emissions.add(testScheduler.currentTime to it) }

            assertEquals(listOf(0L to 1, WINDOW to 2), emissions)
        }

    @Test
    fun `a burst is emitted at no more than one value per window`() =
        runTest {
            val emissions = mutableListOf<Pair<Long, Int>>()

            flow {
                repeat(BURST_SIZE) { index ->
                    emit(index)
                    delay(WITHIN_WINDOW)
                }
            }.throttleLatest(WINDOW).collect { emissions.add(testScheduler.currentTime to it) }

            val emissionTimes = emissions.map { it.first }
            assertEquals(emissionTimes.sorted(), emissionTimes)
            emissionTimes.zipWithNext().forEach { (earlier, later) ->
                assertEquals(WINDOW, later - earlier)
            }
            assertEquals(BURST_SIZE - 1, emissions.last().second)
        }

    companion object {
        private const val WINDOW = 200L
        private const val WITHIN_WINDOW = 50L
        private const val SPACED_APART = 1000L
        private const val BURST_SIZE = 12
    }
}
