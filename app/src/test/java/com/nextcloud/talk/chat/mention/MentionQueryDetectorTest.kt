/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.mention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MentionQueryDetectorTest {

    @Test
    fun emptyTextHasNoQuery() {
        assertNull(MentionQueryDetector.find("", 0))
    }

    @Test
    fun loneAtSignTriggersEmptyQuery() {
        assertEquals(MentionQuery(0, 1, ""), MentionQueryDetector.find("@", 1))
    }

    @Test
    fun wordAtStartIsFound() {
        assertEquals(MentionQuery(0, 4, "ali"), MentionQueryDetector.find("@ali", 4))
    }

    @Test
    fun wordInTheMiddleIsFoundWhileCursorIsInside() {
        val text = "hello @ali how are you"
        assertEquals(MentionQuery(6, 10, "ali"), MentionQueryDetector.find(text, 8))
    }

    @Test
    fun cursorRightBeforeAtSignCounts() {
        assertEquals(MentionQuery(6, 10, "ali"), MentionQueryDetector.find("hello @ali", 6))
    }

    @Test
    fun cursorOutsideOfTheWordHasNoQuery() {
        assertNull(MentionQueryDetector.find("hello @ali how", 13))
        assertNull(MentionQueryDetector.find("hello @ali how", 2))
    }

    @Test
    fun textWithoutAtSignHasNoQuery() {
        assertNull(MentionQueryDetector.find("hello alice", 5))
    }

    @Test
    fun onlyTheFirstAtSignIsStrippedFromTheQuery() {
        assertEquals(MentionQuery(0, 5, "@ali"), MentionQueryDetector.find("@@ali", 5))
    }

    @Test
    fun wordOnSecondLineIsFound() {
        val text = "first line\n@bob"
        assertEquals(MentionQuery(11, 15, "bob"), MentionQueryDetector.find(text, 15))
    }

    @Test
    fun negativeCursorHasNoQuery() {
        assertNull(MentionQueryDetector.find("@ali", -1))
    }
}
