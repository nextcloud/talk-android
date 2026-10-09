/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import kotlin.math.sqrt

/** Turns decoded audio into the bars of a voice message waveform. */
object Waveforms {

    /**
     * Reduces [levels] to [size] bars, each the maximum of an equally sized range of [levels] (repeating levels if
     * there are fewer than [size]), scaled so that the highest bar is 1.
     */
    fun reduce(levels: FloatArray, size: Int): FloatArray {
        if (levels.isEmpty() || size <= 0) {
            return FloatArray(size.coerceAtLeast(0))
        }
        val bars = FloatArray(size) { index ->
            val start = (index.toLong() * levels.size / size).toInt()
            val end = ((index + 1).toLong() * levels.size / size).toInt().coerceIn(start + 1, levels.size)
            var max = 0f
            for (position in start until end) {
                max = maxOf(max, levels[position])
            }
            max
        }
        val highest = bars.max()
        return if (highest > 0f) FloatArray(size) { bars[it] / highest } else bars
    }
}

/** Collects the loudness (root mean square) of consecutive chunks of decoded audio. */
class WaveformLevels {
    private var levels = FloatArray(INITIAL_CAPACITY)

    var size: Int = 0
        private set

    /** Adds the level of a chunk from the sum of its squared samples, each sample being in the range -1..1. */
    fun addChunk(sumOfSquares: Double, sampleCount: Int) {
        if (sampleCount <= 0) {
            return
        }
        if (size == levels.size) {
            levels = levels.copyOf(size * 2)
        }
        levels[size] = sqrt(sumOfSquares / sampleCount).toFloat()
        size++
    }

    fun toArray(): FloatArray = levels.copyOf(size)

    companion object {
        private const val INITIAL_CAPACITY = 256
    }
}
