/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class VoiceMessageDurationLoaderTest {
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "voice-io-test") }
    private val file = File("voice.ogg")

    @After
    fun tearDown() {
        executor.shutdownNow()
    }

    @Test
    fun readRunsOnTheIoDispatcherNotOnTheCallingThread() =
        runBlocking {
            var readThread: String? = null
            val loader = VoiceMessageDurationLoader(executor.asCoroutineDispatcher()) {
                readThread = Thread.currentThread().name
                7
            }

            assertEquals(7, loader.read(file))
            assertTrue(readThread!!.startsWith("voice-io-test"))
            assertNotEquals(Thread.currentThread().name, readThread)
        }

    @Test
    fun secondTapOnTheSameMessageDuringTheReadIsIgnored() =
        runBlocking {
            val reads = AtomicInteger()
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val loader = VoiceMessageDurationLoader(executor.asCoroutineDispatcher()) {
                reads.incrementAndGet()
                started.countDown()
                release.await(5, TimeUnit.SECONDS)
                3
            }

            val first = async(start = CoroutineStart.UNDISPATCHED) { loader.readOnce(1, file) }
            started.await(5, TimeUnit.SECONDS)
            val second = loader.readOnce(1, file)
            release.countDown()

            assertNull(second)
            assertEquals(3, first.await())
            assertEquals(1, reads.get())
        }

    @Test
    fun messageCanBeReadAgainAfterTheReadFinished() =
        runBlocking {
            val loader = VoiceMessageDurationLoader(executor.asCoroutineDispatcher()) { 4 }

            assertEquals(4, loader.readOnce(1, file))
            assertEquals(4, loader.readOnce(1, file))
        }

    @Test
    fun otherMessageIsNotBlockedByARunningRead() =
        runBlocking {
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val loader = VoiceMessageDurationLoader(Dispatchers.Default) {
                if (it.endsWith("a.ogg")) {
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                5
            }

            val first = async(start = CoroutineStart.UNDISPATCHED) { loader.readOnce(1, File("a.ogg")) }
            started.await(5, TimeUnit.SECONDS)
            val second = loader.readOnce(2, File("b.ogg"))
            release.countDown()

            assertEquals(5, second)
            assertEquals(5, first.await())
        }

    @Test
    fun repeatedTapIsIgnoredWhileItsReadRunsAfterAnotherMessageFinished() =
        runBlocking {
            val readsOfA = AtomicInteger()
            val started = CountDownLatch(1)
            val release = CountDownLatch(1)
            val loader = VoiceMessageDurationLoader(Dispatchers.Default) {
                if (it.endsWith("a.ogg")) {
                    readsOfA.incrementAndGet()
                    started.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                6
            }

            val first = async(start = CoroutineStart.UNDISPATCHED) { loader.readOnce(1, File("a.ogg")) }
            started.await(5, TimeUnit.SECONDS)
            assertEquals(6, loader.readOnce(2, File("b.ogg")))
            val repeated = loader.readOnce(1, File("a.ogg"))
            release.countDown()

            assertNull(repeated)
            assertEquals(6, first.await())
            assertEquals(1, readsOfA.get())
        }
}
