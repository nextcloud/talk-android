/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH and Nextcloud contributors
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.graphics.Color
import java.security.MessageDigest
import kotlin.math.abs

// See https://github.com/nextcloud/nextcloud-vue/blob/56b79afae93f4701a0cb933bfeb7b4a2fbd590fb/src/functions/usernameToColor/usernameToColor.js
// and https://github.com/nextcloud/nextcloud-vue/blob/56b79afae93f4701a0cb933bfeb7b4a2fbd590fb/src/utils/GenColors.js

@Suppress("MagicNumber")
class ColorGenerator private constructor() {

    private val steps = 6
    private val finalPalette: List<Int> = genColors(steps)

    companion object {
        val shared = ColorGenerator()
        private const val MULTIPLIER = 256.0f

        private fun stepCalc(steps: Int, colorStart: Int, colorEnd: Int): List<Float> {
            val r0 = Color.red(colorStart) / MULTIPLIER
            val g0 = Color.green(colorStart) / MULTIPLIER
            val b0 = Color.blue(colorStart) / MULTIPLIER

            val r1 = Color.red(colorEnd) / MULTIPLIER
            val g1 = Color.green(colorEnd) / MULTIPLIER
            val b1 = Color.blue(colorEnd) / MULTIPLIER

            return listOf(
                (r1 - r0) / steps,
                (g1 - g0) / steps,
                (b1 - b0) / steps
            )
        }

        private fun mixPalette(steps: Int, color1: Int, color2: Int): List<Int> {
            val palette = mutableListOf(color1)
            val step = stepCalc(steps, color1, color2)

            val rStart = Color.red(color1) / MULTIPLIER
            val gStart = Color.green(color1) / MULTIPLIER
            val bStart = Color.blue(color1) / MULTIPLIER

            for (i in 1 until steps) {
                val r = (abs(rStart + step[0] * i) * MULTIPLIER).toInt().coerceIn(0, 255)
                val g = (abs(gStart + step[1] * i) * MULTIPLIER).toInt().coerceIn(0, 255)
                val b = (abs(bStart + step[2] * i) * MULTIPLIER).toInt().coerceIn(0, 255)

                palette.add(Color.rgb(r, g, b))
            }

            return palette
        }

        fun genColors(steps: Int): List<Int> {
            val red = Color.rgb(
                (182 / MULTIPLIER * 255).toInt(),
                (70 / MULTIPLIER * 255).toInt(),
                (157 / MULTIPLIER * 255).toInt()
            )
            val yellow = Color.rgb(
                (221 / MULTIPLIER * 255).toInt(),
                (203 / MULTIPLIER * 255).toInt(),
                (85 / MULTIPLIER * 255).toInt()
            )
            val blue = Color.rgb(
                0,
                (130 / MULTIPLIER * 255).toInt(),
                (201 / MULTIPLIER * 255).toInt()
            )

            val palette1 = mixPalette(steps, red, yellow).toMutableList()
            val palette2 = mixPalette(steps, yellow, blue)
            val palette3 = mixPalette(steps, blue, red)

            palette1.addAll(palette2)
            palette1.addAll(palette3)

            return palette1
        }
    }

    fun usernameToColor(username: String): Int {
        val hash = username.lowercase()
        var hashInt: Int

        val bytes = hash.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(it) }

        hashInt = hex.map { it.digitToInt(16) % 16 }.sum()

        val maximum = steps * 3
        hashInt %= maximum

        return finalPalette[hashInt]
    }
}
