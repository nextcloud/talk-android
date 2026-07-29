/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import org.junit.Assert
import org.junit.Test

class ResolveReplyToMessageIdTest {

    @Test
    fun replyFromViewModelIsUsed() {
        Assert.assertEquals(7, resolveReplyToMessageId(7, null, 42))
    }

    @Test
    fun replyFromDraftIsUsedWhenViewModelIsEmpty() {
        Assert.assertEquals(7, resolveReplyToMessageId(null, 7, 42))
    }

    @Test
    fun replyFromDraftIsUsedWhenViewModelIsZero() {
        Assert.assertEquals(7, resolveReplyToMessageId(0, 7, 42))
    }

    @Test
    fun threadIsUsedWithoutReply() {
        Assert.assertEquals(42, resolveReplyToMessageId(null, null, 42))
    }

    @Test
    fun zeroWithoutReplyAndThread() {
        Assert.assertEquals(0, resolveReplyToMessageId(null, 0, null))
    }
}
