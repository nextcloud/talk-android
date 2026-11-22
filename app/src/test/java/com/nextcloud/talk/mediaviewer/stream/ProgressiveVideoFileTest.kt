/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

class ProgressiveVideoFileTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun `downloaded prefix can play before the remaining bytes arrive`() {
        val file = folder.newFile().apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)) }
        val stream = ProgressiveVideoFile(8, "version-one")
        stream.attach(file)
        stream.acquire()
        stream.advance(4)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val bytes = ByteArray(8)
            val first = executor.submit<Int> { stream.read(0, bytes, 0, bytes.size) { false } }
            assertEquals(4, first.get(1, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(1, 2, 3, 4, 0, 0, 0, 0), bytes)
            val next = executor.submit<Int> { stream.read(4, bytes, 0, bytes.size) { false } }
            assertThrows(TimeoutException::class.java) { next.get(100, TimeUnit.MILLISECONDS) }
            stream.advance(8)
            assertEquals(4, next.get(1, TimeUnit.SECONDS))
            assertArrayEquals(byteArrayOf(5, 6, 7, 8), bytes.copyOf(4))
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(1, TimeUnit.SECONDS)
            stream.release()
        }
    }

    @Test(timeout = 5000)
    fun `reader survives atomic publication and can reopen the finished file`() {
        val temporary = folder.newFile().apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val target = folder.root.resolve("complete.mp4")
        val stream = ProgressiveVideoFile(3, null)
        stream.attach(temporary)
        stream.advance(3)
        stream.acquire()
        try {
            val bytes = ByteArray(3)
            assertEquals(1, stream.read(0, bytes, 0, 1) { false })
            assertTrue(stream.publish(temporary, target))
            assertEquals(3, stream.read(0, bytes, 0, 3) { false })
            assertArrayEquals(byteArrayOf(1, 2, 3), bytes)
            assertEquals(-1, stream.read(3, bytes, 0, 3) { false })
        } finally {
            stream.release()
        }
    }

    @Test
    fun `unknown length only ends after download completion`() {
        val file = folder.newFile()
        val stream = ProgressiveVideoFile(-1, null)
        stream.attach(file)
        assertTrue(stream.publish(file, folder.root.resolve("unknown.mp4")))
        assertEquals(-1, stream.read(0, ByteArray(1), 0, 1) { false })
    }

    @Test
    fun `failed or cancelled download cannot wait forever`() {
        val stream = ProgressiveVideoFile(100, null)
        stream.fail()
        assertThrows(IOException::class.java) { stream.read(0, ByteArray(1), 0, 1) { false } }
        val cancelled = ProgressiveVideoFile(100, null)
        assertThrows(InterruptedIOException::class.java) { cancelled.read(0, ByteArray(1), 0, 1) { true } }
    }

    @Test
    fun `seeking needs a range only outside available bytes with a validator`() {
        val stream = ProgressiveVideoFile(100, "version-one")
        stream.advance(10)
        assertFalse(stream.needsRange(0))
        assertFalse(stream.needsRange(10))
        assertTrue(stream.needsRange(90))
        assertFalse(ProgressiveVideoFile(100, null).needsRange(90))
        assertFalse(ProgressiveVideoFile(-1, "version-one").needsRange(90))
    }
}
