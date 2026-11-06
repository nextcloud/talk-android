/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import com.nextcloud.talk.chat.data.model.ChatMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val EXPECTED_MESSAGE_LIMIT = 100

class MessageSelectionTest {

    private val selection = MessageSelection()

    @Test
    fun `a new selection is not active`() {
        assertFalse(selection.isActive)
        assertEquals(emptySet<Int>(), selection.ids.value)
    }

    @Test
    fun `toggling a message selects it and starts the selection`() {
        selection.toggle(7, null)

        assertTrue(selection.isActive)
        assertEquals(setOf(7), selection.ids.value)
    }

    @Test
    fun `toggling a selected message deselects it`() {
        selection.toggle(7, null)
        selection.toggle(8, null)
        selection.toggle(7, null)

        assertEquals(setOf(8), selection.ids.value)
    }

    @Test
    fun `deselecting the last message ends the selection`() {
        selection.toggle(7, null)
        selection.toggle(7, null)

        assertFalse(selection.isActive)
    }

    @Test
    fun `back ends an active selection and is handled`() {
        selection.toggle(7, null)
        selection.toggle(8, null)

        assertTrue(selection.consumeBack())
        assertFalse(selection.isActive)
        assertEquals(emptySet<Int>(), selection.ids.value)
    }

    @Test
    fun `back without a selection is not handled`() {
        assertFalse(selection.consumeBack())
    }

    @Test
    fun `ids that left the chat are dropped from the selection`() {
        selection.toggle(-3, null)
        selection.toggle(7, null)
        selection.toggle(8, null)

        selection.retainOnly(setOf(7, 8, 9))
        selection.retainOnly(setOf(7, 9))

        assertEquals(setOf(7), selection.ids.value)
    }

    @Test
    fun `dropping the last selected id ends the selection`() {
        selection.toggle(-3, null)

        selection.retainOnly(setOf(7))

        assertFalse(selection.isActive)
    }

    @Test
    fun `the hundredth message is selected but the next one is rejected`() {
        repeat(EXPECTED_MESSAGE_LIMIT) { assertEquals(null, selection.toggle(it + 1, "x")) }

        assertEquals(SelectionLimit.MESSAGE_COUNT, selection.toggle(101, "x"))
        assertEquals(100, selection.ids.value.size)
    }

    @Test
    fun `exactly 32000 forwarded characters fit but one more does not`() {
        assertEquals(null, selection.toggle(1, "x".repeat(31_999)))
        assertEquals(null, selection.toggle(2, "x"))

        assertEquals(SelectionLimit.TEXT_LENGTH, selection.toggle(3, "x"))
        assertEquals(setOf(1, 2), selection.ids.value)
    }

    @Test
    fun `deselecting after either limit succeeds and frees capacity`() {
        repeat(EXPECTED_MESSAGE_LIMIT) { selection.toggle(it + 1, "x") }
        assertEquals(SelectionLimit.MESSAGE_COUNT, selection.toggle(101, "x"))
        assertEquals(null, selection.toggle(1, null))
        assertEquals(null, selection.toggle(101, "x"))

        selection.clear()
        selection.toggle(1, "x".repeat(32_000))
        assertEquals(SelectionLimit.TEXT_LENGTH, selection.toggle(2, "x"))
        assertEquals(null, selection.toggle(1, null))
        assertEquals(null, selection.toggle(2, "x"))
    }

    @Test
    fun `a pending message with a negative id cannot be selected`() {
        assertFalse(isSelectableMessageId(-1))
        assertFalse(isSelectableMessageId(0))
        assertTrue(isSelectableMessageId(1))
    }

    @Test
    fun `a message that fails to load is left out of the loaded selection`() {
        val loaded = runBlocking {
            loadSelected(listOf(-3, 7, 8)) { id ->
                if (id < 0) throw IOException("HTTP 404") else id
            }
        }

        assertEquals(listOf(7, 8), loaded)
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is not swallowed while loading the selection`() {
        runBlocking { loadSelected(listOf(7)) { throw CancellationException("cancelled") } }
    }
}

class ForwardPlanTest {

    private fun message(id: Int, text: String?): ChatMessage =
        ChatMessage().apply {
            jsonMessageId = id
            message = text
        }

    @Test
    fun `each forwardable message is its own text in chat order`() {
        val plan = planForward(listOf(message(30, "c"), message(10, "a"), message(20, "b"))) { true }

        assertEquals(listOf("a", "b", "c"), plan.texts)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun `messages that cannot be forwarded are counted as skipped`() {
        val plan = planForward(listOf(message(1, "a"), message(2, "{file}"), message(3, "c"))) { it.jsonMessageId != 2 }

        assertEquals(listOf("a", "c"), plan.texts)
        assertEquals(1, plan.skipped)
    }

    @Test
    fun `nothing forwardable gives no texts`() {
        val plan = planForward(listOf(message(1, "{file}"))) { false }

        assertTrue(plan.texts.isEmpty())
        assertEquals(1, plan.skipped)
    }

    @Test
    fun `everything forwardable is sent without a notice`() {
        assertEquals(ForwardOutcome.ALL, planForward(listOf(message(1, "a"), message(2, "b"))) { true }.outcome)
    }

    @Test
    fun `left out messages give the skipped notice and the rest is sent`() {
        val plan = planForward(listOf(message(1, "a"), message(2, "{file}"))) { it.jsonMessageId == 1 }

        assertEquals(ForwardOutcome.SOME_SKIPPED, plan.outcome)
        assertEquals(listOf("a"), plan.texts)
    }

    @Test
    fun `an empty plan is reported and not silent`() {
        assertEquals(ForwardOutcome.NOTHING_TO_FORWARD, planForward(listOf(message(1, "a"))) { false }.outcome)
        assertEquals(ForwardOutcome.NOTHING_TO_FORWARD, planForward(emptyList()) { true }.outcome)
        assertEquals(ForwardOutcome.NOTHING_TO_FORWARD, planForward(listOf(message(1, "  "))) { true }.outcome)
    }
}
