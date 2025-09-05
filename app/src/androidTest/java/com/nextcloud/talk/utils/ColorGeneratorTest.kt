/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Nextcloud GmbH and Nextcloud contributors
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.graphics.Color
import org.junit.Assert
import org.junit.Test

class ColorGeneratorTest {

    @Test
    fun testUsernameToColor() {
        usernameToColorHexHelper("", "#0082c9")
        usernameToColorHexHelper(",", "#1e78c1")
        usernameToColorHexHelper(".", "#c98879")
        usernameToColorHexHelper("admin", "#d09e6d")
        usernameToColorHexHelper("123e4567-e89b-12d3-a456-426614174000", "#bc5c91")
        usernameToColorHexHelper("Akeel Robertson", "#9750a4")
        usernameToColorHexHelper("Brayden Truong", "#d09e6d")
        usernameToColorHexHelper("Daphne Roy", "#9750a4")
        usernameToColorHexHelper("Ellena Wright Frederic Conway", "#c37285")
        usernameToColorHexHelper("Gianluca Hills", "#d6b461")
        usernameToColorHexHelper("Haseeb Stephens", "#d6b461")
        usernameToColorHexHelper("Idris Mac", "#9750a4")
        usernameToColorHexHelper("Kristi Fisher", "#0082c9")
        usernameToColorHexHelper("Lillian Wall", "#bc5c91")
        usernameToColorHexHelper("Lorelai Taylor", "#ddcb55")
        usernameToColorHexHelper("Madina Knight", "#9750a4")
        usernameToColorHexHelper("Meeting", "#c98879")
        usernameToColorHexHelper("Private Circle", "#c37285")
        usernameToColorHexHelper("Rae Hope", "#795aab")
        usernameToColorHexHelper("Santiago Singleton", "#bc5c91")
        usernameToColorHexHelper("Sid Combs", "#d09e6d")
        usernameToColorHexHelper("TestCircle", "#499aa2")
        usernameToColorHexHelper("Tom Mörtel", "#248eb5")
        usernameToColorHexHelper("Vivienne Jacobs", "#1e78c1")
        usernameToColorHexHelper("Zaki Cortes", "#6ea68f")
        usernameToColorHexHelper("a user", "#5b64b3")
        usernameToColorHexHelper("admin@cloud.example.com", "#9750a4")
        usernameToColorHexHelper("another user", "#ddcb55")
        usernameToColorHexHelper("asd", "#248eb5")
        usernameToColorHexHelper("bar", "#0082c9")
        usernameToColorHexHelper("foo", "#d09e6d")
        usernameToColorHexHelper("wasd", "#b6469d")
        usernameToColorHexHelper("مرحبا بالعالم", "#c98879")
        usernameToColorHexHelper("🙈", "#b6469d")
    }

    private fun usernameToColorHexHelper(username: String, expectedHexColor: String) {
        val userColorInt = ColorGenerator.usernameToColor(username) // returns Int
        val userHexColor = intToHex(userColorInt)

        Assert.assertEquals(expectedHexColor.lowercase(), userHexColor.lowercase())
    }

    private fun intToHex(colorInt: Int): String {
        val r = Color.red(colorInt)
        val g = Color.green(colorInt)
        val b = Color.blue(colorInt)
        return String.format("#%02x%02x%02x", r, g, b)
    }
}
