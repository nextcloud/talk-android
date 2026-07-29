/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_TEAMS
import org.junit.Assert.assertEquals
import org.junit.Test

class MentionAutocompleteAdapterTest {

    @Test
    fun teamSourceShowsTeamLabelInsteadOfObjectId() {
        assertEquals(
            "Team",
            MentionAutocompleteAdapter.secondaryText(SOURCE_TEAMS, "team/RFu2UR8oOhEaI6OKUlQ", "Team")
        )
    }

    @Test
    fun nonTeamSourceShowsMentionHandle() {
        assertEquals(
            "@admin",
            MentionAutocompleteAdapter.secondaryText("users", "admin", "Team")
        )
    }
}
