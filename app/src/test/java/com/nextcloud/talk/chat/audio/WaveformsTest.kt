/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class WaveformsTest {

    private val delta = 0.0001f

    @Test
    fun `levels are reduced to the maximum of each range and normalized`() {
        val levels = floatArrayOf(0.1f, 0.2f, 0.4f, 0.1f, 0.05f, 0.1f)

        assertArrayEquals(floatArrayOf(0.5f, 1f, 0.25f), Waveforms.reduce(levels, 3), delta)
    }

    @Test
    fun `every level is taken into account, also if the sizes do not divide`() {
        val levels = FloatArray(100) { 0.1f }.also { it[99] = 0.8f }

        val bars = Waveforms.reduce(levels, 30)

        assertEquals(30, bars.size)
        assertEquals(1f, bars.last(), delta)
    }

    @Test
    fun `fewer levels than bars are stretched`() {
        assertArrayEquals(floatArrayOf(0.5f, 0.5f, 1f, 1f), Waveforms.reduce(floatArrayOf(0.2f, 0.4f), 4), delta)
    }

    @Test
    fun `silence and empty input give flat bars`() {
        assertArrayEquals(FloatArray(3), Waveforms.reduce(FloatArray(5), 3), delta)
        assertArrayEquals(FloatArray(3), Waveforms.reduce(FloatArray(0), 3), delta)
        assertEquals(0, Waveforms.reduce(floatArrayOf(1f), 0).size)
    }

    @Test
    fun `levels are the root mean square of each chunk`() {
        val levels = WaveformLevels()

        levels.addChunk(sumOfSquares = 4 * 0.25, sampleCount = 4)
        levels.addChunk(sumOfSquares = 1.0, sampleCount = 0)
        repeat(300) { levels.addChunk(sumOfSquares = 0.0, sampleCount = 1) }

        assertEquals(301, levels.size)
        assertEquals(0.5f, levels.toArray()[0], delta)
        assertEquals(0f, levels.toArray()[300], delta)
    }
}
