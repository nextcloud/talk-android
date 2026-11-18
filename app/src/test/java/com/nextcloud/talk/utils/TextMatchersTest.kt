/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class TextMatchersTest {

    @Test
    fun `a single emoji is detected`() {
        assertTrue(TextMatchers.isMessageWithSingleEmoticonOnly("😀"))
    }

    @Test
    fun `a single emoji surrounded by whitespace is detected`() {
        assertTrue(TextMatchers.isMessageWithSingleEmoticonOnly(" 😀\n"))
    }

    @Test
    fun `an emoji with a skin tone is detected`() {
        assertTrue(TextMatchers.isMessageWithSingleEmoticonOnly("👍🏽"))
    }

    @Test
    fun `an emoji drawn as emoji by its presentation selector is detected`() {
        assertTrue(TextMatchers.isMessageWithSingleEmoticonOnly("❤️"))
    }

    @Test
    fun `a flag is detected`() {
        assertTrue(TextMatchers.isMessageWithSingleEmoticonOnly("🇩🇪"))
    }

    @Test
    fun `emoji joined into one are detected`() {
        assertTrue(
            TextMatchers.isMessageWithSingleEmoticonOnly(
                "👨‍👩‍👧"
            )
        )
    }

    @Test
    fun `a keycap is detected`() {
        assertTrue(TextMatchers.isMessageWithSingleEmoticonOnly("1️⃣"))
    }

    @Test
    fun `text with an emoji is not a single emoji`() {
        assertFalse(TextMatchers.isMessageWithSingleEmoticonOnly("hi 😀"))
    }

    @Test
    fun `two emoji are not a single emoji`() {
        assertFalse(TextMatchers.isMessageWithSingleEmoticonOnly("😀😀"))
    }

    @Test
    fun `a digit is not a single emoji`() {
        assertFalse(TextMatchers.isMessageWithSingleEmoticonOnly("1"))
    }

    @Test
    fun `a letter is not a single emoji`() {
        assertFalse(TextMatchers.isMessageWithSingleEmoticonOnly("a"))
    }

    @Test
    fun `blank text is not a single emoji`() {
        assertFalse(TextMatchers.isMessageWithSingleEmoticonOnly(" "))
    }
}
