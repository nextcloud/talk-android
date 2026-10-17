/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.app.Application
import android.content.ComponentName
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A tap on a chat row outside of the bubble opens the message menu and a long press there selects the message. The
 * bubble keeps the behaviour it had before: a tap and a long press open the menu. While a selection is active a tap
 * anywhere on the row toggles the selection. Incoming messages sit at the start of the row, so its end is empty.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatMessageViewSelectionTest {

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

    private val menuOpened = mutableListOf<Int>()
    private val toggled = mutableListOf<Int>()

    private fun show(selectionActive: Boolean = false, checked: Boolean = false, selectable: Boolean = true) {
        composeRule.setContent {
            CompositionLocalProvider(LocalMessageLongClickHandler provides { menuOpened += it }) {
                MessageRowFrame(
                    selection = MessageRowSelection(
                        active = selectionActive,
                        checked = checked,
                        selectable = selectable,
                        onToggle = { toggled += MESSAGE_ID }
                    ),
                    onTap = { menuOpened += MESSAGE_ID },
                    onOpenMenu = { menuOpened += MESSAGE_ID }
                ) {
                    Box(modifier = Modifier.padding(horizontal = 12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(BUBBLE_WIDTH, BUBBLE_HEIGHT)
                                .testTag(TAG_BUBBLE)
                                .openMenuOnClick(MESSAGE_ID)
                        )
                    }
                }
            }
        }
    }

    private fun tapEmptyPartOfRow() {
        composeRule.onNodeWithTag(TAG_MESSAGE_ROW).performTouchInput {
            click(Offset(width - EDGE_OFFSET_PX, centerY))
        }
        // the row waits for a possible double tap before it takes the gesture for a tap
        composeRule.mainClock.advanceTimeBy(DOUBLE_TAP_WAIT_MILLIS)
    }

    private fun longPressEmptyPartOfRow() {
        composeRule.onNodeWithTag(TAG_MESSAGE_ROW).performTouchInput {
            longClick(Offset(width - EDGE_OFFSET_PX, centerY))
        }
    }

    @Test
    fun `tap outside of the bubble opens the menu`() {
        show()

        tapEmptyPartOfRow()

        assertEquals(listOf(MESSAGE_ID), menuOpened)
        assertEquals(emptyList<Int>(), toggled)
    }

    @Test
    fun `long press outside of the bubble selects the message and does not open the menu`() {
        show()

        longPressEmptyPartOfRow()

        assertEquals(listOf(MESSAGE_ID), toggled)
        assertEquals(emptyList<Int>(), menuOpened)
    }

    @Test
    fun `tap on the bubble opens the menu`() {
        show()

        composeRule.onNodeWithTag(TAG_BUBBLE).performTouchInput { click() }

        assertEquals(listOf(MESSAGE_ID), menuOpened)
        assertEquals(emptyList<Int>(), toggled)
    }

    @Test
    fun `long press on the bubble opens the menu and does not select the message`() {
        show()

        composeRule.onNodeWithTag(TAG_BUBBLE).performTouchInput { longClick() }

        assertEquals(listOf(MESSAGE_ID), menuOpened)
        assertEquals(emptyList<Int>(), toggled)
    }

    @Test
    fun `tap outside of the bubble toggles the message while a selection is active`() {
        show(selectionActive = true, checked = true)

        tapEmptyPartOfRow()

        assertEquals(listOf(MESSAGE_ID), toggled)
        assertEquals(emptyList<Int>(), menuOpened)
    }

    @Test
    fun `tap on the bubble toggles the message while a selection is active`() {
        show(selectionActive = true)

        composeRule.onNodeWithTag(TAG_BUBBLE).performTouchInput { click() }

        assertEquals(listOf(MESSAGE_ID), toggled)
        assertEquals(emptyList<Int>(), menuOpened)
    }

    @Test
    fun `long press on the bubble toggles the message while a selection is active`() {
        show(selectionActive = true)

        composeRule.onNodeWithTag(TAG_BUBBLE).performTouchInput { longClick() }

        assertEquals(listOf(MESSAGE_ID), toggled)
        assertEquals(emptyList<Int>(), menuOpened)
    }

    @Test
    fun `the indicator shows whether the message is selected`() {
        show(selectionActive = true, checked = true)
        composeRule.onNodeWithTag(TAG_SELECTION_INDICATOR, useUnmergedTree = true).assertIsSelected()
    }

    @Test
    fun `the indicator of a message that is not selected is not selected`() {
        show(selectionActive = true, checked = false)
        composeRule.onNodeWithTag(TAG_SELECTION_INDICATOR, useUnmergedTree = true).assertIsNotSelected()
    }

    @Test
    fun `no indicator is shown without a selection`() {
        show()
        composeRule.onNodeWithTag(TAG_SELECTION_INDICATOR, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `a message that cannot be selected opens the menu on a long press`() {
        show(selectable = false)

        longPressEmptyPartOfRow()

        assertEquals(listOf(MESSAGE_ID), menuOpened)
        assertEquals(emptyList<Int>(), toggled)
    }

    private companion object {
        const val MESSAGE_ID = 1
        const val TAG_BUBBLE = "bubble"
        const val EDGE_OFFSET_PX = 4f
        const val DOUBLE_TAP_WAIT_MILLIS = 500L
        val BUBBLE_WIDTH = 100.dp
        val BUBBLE_HEIGHT = 40.dp
    }
}
