/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.viewmodels

import com.nextcloud.talk.chat.data.model.ChatMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSelectionTest {

    private val selection = MessageSelection()

    @Test
    fun `a new selection is not active`() {
        assertFalse(selection.isActive)
        assertEquals(emptySet<Int>(), selection.ids.value)
    }

    @Test
    fun `toggling a message selects it and starts the selection`() {
        selection.toggle(7)

        assertTrue(selection.isActive)
        assertEquals(setOf(7), selection.ids.value)
    }

    @Test
    fun `toggling a selected message deselects it`() {
        selection.toggle(7)
        selection.toggle(8)
        selection.toggle(7)

        assertEquals(setOf(8), selection.ids.value)
    }

    @Test
    fun `deselecting the last message ends the selection`() {
        selection.toggle(7)
        selection.toggle(7)

        assertFalse(selection.isActive)
    }

    @Test
    fun `back ends an active selection and is handled`() {
        selection.toggle(7)
        selection.toggle(8)

        assertTrue(selection.consumeBack())
        assertFalse(selection.isActive)
        assertEquals(emptySet<Int>(), selection.ids.value)
    }

    @Test
    fun `back without a selection is not handled`() {
        assertFalse(selection.consumeBack())
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
