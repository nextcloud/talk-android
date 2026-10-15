/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui.chat

import androidx.compose.ui.graphics.Color
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_TEAMS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionSuggestionTextTest {

    @Test
    fun teamSourceShowsTeamLabelInsteadOfObjectId() {
        assertEquals("Team", mentionSecondaryText(SOURCE_TEAMS, "team/RFu2UR8oOhEaI6OKUlQ", "Team"))
    }

    @Test
    fun nonTeamSourceShowsMentionHandle() {
        assertEquals("@admin", mentionSecondaryText("users", "admin", "Team"))
    }

    @Test
    fun emptyQueryHighlightsNothing() {
        assertTrue(highlightQuery("Alice", "", Color.Red).spanStyles.isEmpty())
    }

    @Test
    fun everyCaseInsensitiveOccurrenceIsHighlighted() {
        val ranges = highlightQuery("Anna Banana", "an", Color.Red).spanStyles.map { it.start until it.end }
        assertEquals(listOf(0 until 2, 6 until 8, 8 until 10), ranges)
    }
}
