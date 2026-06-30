/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.logger

import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

@Suppress("TooManyFunctions")
class LoggerImpl(private val handler: FileLogHandler, queueCapacity: Int = DEFAULT_QUEUE_CAPACITY) :
    Logger,
    LogsRepository {

    companion object {
        private const val DEFAULT_QUEUE_CAPACITY = 1000
        const val PREFS_NAME = "logger_prefs"
        const val PREF_LOG_LEVEL = "log_level"
        val DEFAULT_LEVEL = Level.WARNING
    }

    private data class Load(val onResult: (List<LogEntry>, Long) -> Unit)
    private class Delete
    private class Flush(val latch: CountDownLatch)

    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private val eventQueue = LinkedBlockingQueue<Any>(queueCapacity)
    private val processedEvents = mutableListOf<Any>()
    private val otherEvents = mutableListOf<Any>()
    private val missedLogs = AtomicBoolean()
    private val missedLogsCount = AtomicLong()

    private val thread = Thread {
        while (!Thread.currentThread().isInterrupted) {
            try {
                eventLoop()
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
    }.apply {
        isDaemon = true
        name = "NcTalkLoggerThread"
    }

    override val lostEntries: Boolean get() = missedLogs.get()

    @Volatile
    override var minimumLevel: Level = DEFAULT_LEVEL

    fun start() {
        thread.start()
    }

    fun flush(timeoutMs: Long = 2000L) {
        val latch = CountDownLatch(1)
        if (eventQueue.offer(Flush(latch), 1, TimeUnit.SECONDS)) {
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        }
    }

    override fun d(tag: String, message: String) {
        Log.d(tag, message)
        enqueue(Level.DEBUG, tag, message)
    }

    override fun d(tag: String, message: String, t: Throwable) {
        Log.d(tag, message, t)
        enqueue(Level.DEBUG, tag, "$message\n${Log.getStackTraceString(t)}")
    }

    override fun i(tag: String, message: String) {
        Log.i(tag, message)
        enqueue(Level.INFO, tag, message)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
        enqueue(Level.WARNING, tag, message)
    }

    override fun e(tag: String, message: String) {
        Log.e(tag, message)
        enqueue(Level.ERROR, tag, message)
    }

    override fun e(tag: String, message: String, t: Throwable) {
        Log.e(tag, message, t)
        enqueue(Level.ERROR, tag, "$message\n${Log.getStackTraceString(t)}")
    }

    override fun load(onLoaded: (entries: List<LogEntry>, totalLogSize: Long) -> Unit) {
        eventQueue.put(Load(onLoaded))
    }

    override fun deleteAll() {
        eventQueue.put(Delete())
    }

    private fun enqueue(level: Level, tag: String, message: String) {
        if (level.ordinal < minimumLevel.ordinal) return
        try {
            val entry = LogEntry(
                timestamp = Date(),
                level = level,
                tag = tag,
                message = message,
                pid = Process.myPid(),
                tid = Process.myTid()
            )
            val enqueued = eventQueue.offer(entry, 1, TimeUnit.SECONDS)
            if (!enqueued) {
                missedLogs.set(true)
                missedLogsCount.incrementAndGet()
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }

    private fun eventLoop() {
        processedEvents.clear()
        otherEvents.clear()

        processedEvents.add(eventQueue.take())
        eventQueue.drainTo(processedEvents)

        handler.open()
        for (event in processedEvents) {
            if (event is LogEntry) {
                handler.write(event.toString() + "\n")
            } else {
                otherEvents.add(event)
            }
        }
        handler.close()

        for (event in otherEvents) {
            when (event) {
                is Load -> {
                    val raw = handler.loadLogFiles()
                    val entries = LogEntry.parseLines(raw.lines)
                    mainThreadHandler.post { event.onResult(entries, raw.logSize) }
                }
                is Delete -> handler.deleteAll()
                is Flush -> event.latch.countDown()
            }
        }

        val lostCount = missedLogsCount.getAndSet(0)
        if (lostCount > 0) {
            handler.open()
            handler.write(
                LogEntry(
                    timestamp = Date(),
                    level = Level.WARNING,
                    tag = "Logger",
                    message = "Logger queue overflow. Approx $lostCount entries lost."
                ).toString() + "\n"
            )
            handler.close()
        }
    }
}
