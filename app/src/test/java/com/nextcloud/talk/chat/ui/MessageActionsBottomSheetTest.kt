/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.ui

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlinx.coroutines.runBlocking

/**
 * The message menu is taller than half of the window (13 entries on a foldable). A default sheet state opens it at
 * the half-height peek and cuts off the last entry (Delete); the menu must open fully expanded instead.
 * The M3 sheet needs Android resources, which unit tests do not have, so the sheet state is checked directly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MessageActionsBottomSheetTest {

    private val registerActivity = object : ExternalResource() {
        override fun before() {
            val application = ApplicationProvider.getApplicationContext<Application>()
            shadowOf(application.packageManager)
                .addActivityIfNotPresent(ComponentName(application.packageName, ComponentActivity::class.java.name))
        }
    }
    private val composeRule = createComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(registerActivity).around(composeRule)

    @Test
    fun sheetSkipsThePartiallyExpandedPeek() {
        var state: SheetState? = null
        composeRule.setContent { state = rememberMessageActionsSheetState() }
        composeRule.waitForIdle()

        assertNotNull(state)
        // A sheet that skips the peek refuses to settle at the half-height state.
        assertThrows(IllegalStateException::class.java) { runBlocking { state!!.partialExpand() } }
    }
}
