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

// See https://github.com/nextcloud/nextcloud-vue/blob/56b79afae93f4701a0cb933bfeb7b4a2fbd590fb/src/functions/usernameToColor/usernameToColor.js
// and https://github.com/nextcloud/nextcloud-vue/blob/56b79afae93f4701a0cb933bfeb7b4a2fbd590fb/src/utils/GenColors.js

@Suppress("MagicNumber")
object ColorGenerator {

    private const val STEPS = 6
    private val finalPalette: List<RGB> = genColors()

    private data class RGB(val r: Int, val g: Int, val b: Int)

    fun usernameToColor(username: String): Int {
        var hash = username.lowercase()

        val md5Regex = Regex("^([0-9a-f]{4}-?){8}$")
        if (!hash.matches(md5Regex)) {
            val digest = MessageDigest.getInstance("MD5").digest(hash.toByteArray(Charsets.UTF_8))
            hash = digest.joinToString("") { "%02x".format(it) }
        }

        hash = hash.replace(Regex("[^0-9a-f]"), "")

        val idx = hashToInt(hash, finalPalette.size)

        val rgb = finalPalette[idx]
        return Color.rgb(rgb.r, rgb.g, rgb.b)
    }

    private fun hashToInt(hash: String, maximum: Int): Int {
        val sum = hash.map { it.lowercaseChar().digitToInt(16) % 16 }.sum()
        return sum % maximum
    }

    private fun genColors(): List<RGB> {
        val red = RGB(182, 70, 157)
        val yellow = RGB(221, 203, 85)
        val blue = RGB(0, 130, 201)

        return mixPalette(red, yellow) + mixPalette(yellow, blue) + mixPalette(blue, red)
    }

    private fun mixPalette(start: RGB, end: RGB): List<RGB> {
        val palette = mutableListOf(start)
        val rStep = (end.r - start.r).toFloat() / STEPS
        val gStep = (end.g - start.g).toFloat() / STEPS
        val bStep = (end.b - start.b).toFloat() / STEPS

        for (i in 1 until STEPS) {
            val r = (start.r + rStep * i).toInt().coerceIn(0, 255)
            val g = (start.g + gStep * i).toInt().coerceIn(0, 255)
            val b = (start.b + bStep * i).toInt().coerceIn(0, 255)
            palette.add(RGB(r, g, b))
        }
        return palette
    }
}
