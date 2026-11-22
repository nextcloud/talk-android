/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.io.RandomAccessFile

class CacheFileWriterTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `750 MiB file uses bounded reads and is published only when complete`() {
        val target = File(folder.root, "video.mp4")
        var remaining = LARGE_FILE_SIZE
        var lastProgress = 0L
        val input = object : InputStream() {
            override fun read(): Int = error("Bulk reads required")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                assertTrue(length <= DEFAULT_BUFFER_SIZE)
                if (remaining == 0L) return -1
                val count = minOf(length.toLong(), remaining).toInt()
                buffer.fill(CONTENT_BYTE, offset, offset + count)
                remaining -= count
                return count
            }
        }
        CacheFileWriter.write(input, target, LARGE_FILE_SIZE, { false }) {
            assertFalse(target.exists())
            assertTrue(it > lastProgress)
            lastProgress = it
        }
        assertEquals(LARGE_FILE_SIZE, target.length())
        assertEquals(LARGE_FILE_SIZE, lastProgress)
        RandomAccessFile(target, "r").use {
            assertEquals(CONTENT_BYTE.toInt(), it.read())
            it.seek(LARGE_FILE_SIZE - 1)
            assertEquals(CONTENT_BYTE.toInt(), it.read())
        }
        assertEquals(listOf("video.mp4"), folder.root.list()!!.toList())
    }

    @Test
    fun `truncated response removes partial file and preserves existing cache`() {
        val target = folder.newFile("existing")
        target.writeText("original")
        assertThrows(IOException::class.java) {
            CacheFileWriter.write(byteArrayOf(1, 2).inputStream(), target, 3, { false }) {}
        }
        assertEquals("original", target.readText())
        assertEquals(listOf("existing"), folder.root.list()!!.toList())
    }

    @Test
    fun `cancellation removes partial output`() {
        val target = File(folder.root, "cancelled")
        var stopped = false
        assertThrows(InterruptedIOException::class.java) {
            CacheFileWriter.write(ByteArray(DEFAULT_BUFFER_SIZE * 2).inputStream(), target, -1, { stopped }) {
                stopped = true
            }
        }
        assertTrue(folder.root.list()!!.isEmpty())
    }

    @Test
    fun `network read failure removes partial output`() {
        val target = File(folder.root, "failed")
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("Connection interrupted")
        }
        assertThrows(IOException::class.java) {
            CacheFileWriter.write(input, target, -1, { false }) {}
        }
        assertTrue(folder.root.list()!!.isEmpty())
    }

    @Test
    fun `unknown length response preserves exact bytes`() {
        val target = File(folder.root, "chunked")
        val bytes = byteArrayOf(0, 1, -1, 5)
        CacheFileWriter.write(bytes.inputStream(), target, -1, { false }) {}
        assertArrayEquals(bytes, target.readBytes())
    }

    companion object {
        private const val LARGE_FILE_SIZE = 750L * 1024 * 1024
        private const val CONTENT_BYTE: Byte = 37
    }
}
