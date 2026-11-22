/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.mediaviewer.stream

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.io.RandomAccessFile

/** A reader of the committed prefix of a download; an incomplete file is never reported as EOF. */
class ProgressiveVideoFile(val length: Long, val validator: String?) {
    private val monitor = Object()
    private var file: File? = null
    private var reader: RandomAccessFile? = null
    private var readers = 0
    private var available = 0L
    private var complete = false
    private var failure: IOException? = null

    fun attach(temporary: File) = synchronized(monitor) { file = temporary }

    fun advance(bytes: Long) =
        synchronized(monitor) {
            available = bytes
            monitor.notifyAll()
        }

    fun publish(temporary: File, target: File): Boolean =
        synchronized(monitor) {
            reader?.close()
            reader = null
            val published = temporary.renameTo(target)
            if (published) {
                file = target
                complete = true
                monitor.notifyAll()
            }
            published
        }

    fun fail() =
        synchronized(monitor) {
            if (!complete) failure = IOException("Video download did not complete")
            monitor.notifyAll()
        }

    fun acquire() = synchronized(monitor) { readers++ }

    fun release() =
        synchronized(monitor) {
            readers--
            if (readers == 0) {
                reader?.close()
                reader = null
            }
        }

    fun needsRange(position: Long): Boolean =
        synchronized(monitor) {
            position > available && position < length && !complete && validator != null
        }

    @Suppress("LongParameterList")
    fun read(position: Long, buffer: ByteArray, offset: Int, count: Int, cancelled: () -> Boolean): Int =
        synchronized(monitor) {
            while (true) {
                if (cancelled() || Thread.currentThread().isInterrupted) throw InterruptedIOException("Read cancelled")
                failure?.let { throw it }
                if (count == 0) return@synchronized 0
                if (position < available) {
                    val input = reader ?: RandomAccessFile(checkNotNull(file), "r").also { reader = it }
                    input.seek(position)
                    return@synchronized input.read(buffer, offset, minOf(count.toLong(), available - position).toInt())
                }
                if (complete || (length >= 0 && position >= length)) return@synchronized -1
                try {
                    monitor.wait(WAIT_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Read cancelled")
                }
            }
            @Suppress("UNREACHABLE_CODE")
            -1
        }

    companion object {
        private const val WAIT_INTERVAL_MS = 100L
    }
}
