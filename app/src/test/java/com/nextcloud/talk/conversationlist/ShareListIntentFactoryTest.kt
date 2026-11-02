/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist

import android.app.Application
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import androidx.core.content.pm.ShortcutManagerCompat
import com.nextcloud.talk.utils.InternalEntryPoint
import com.nextcloud.talk.utils.bundle.BundleKeys
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * ShareActivity is exported: of the intent another app shares, the conversation list only gets the text or copies of
 * the files it may upload, and the id of a direct share target the app published.
 */
// runBlocking, not runTest: the factory copies on the real IO dispatcher, and runTest would also report exceptions
// that coroutines of earlier test classes leaked
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class ShareListIntentFactoryTest {

    private val context = RuntimeEnvironment.getApplication()

    // the test has no file provider paths: a copy is shared with its file uri
    private val factory = ShareListIntentFactory(
        context,
        uriOfCopy = { Uri.fromFile(it) },
        isPublishedShareTarget = { it == PUBLISHED_SHORTCUT_ID }
    )

    private val photoOfOtherApp = Uri.parse("content://com.example.gallery/photos/photo.jpg")

    private val shareDirectories = File(context.filesDir, "shared_file_copies")

    @Before
    fun setUp() {
        shareDirectories.deleteRecursively()
    }

    private fun shareOfOtherApp(type: String) =
        Intent(Intent.ACTION_SEND)
            .setType(type)
            .putExtra(BundleKeys.KEY_INTERNAL_USER_ID, 1L)
            .putExtra(BundleKeys.KEY_ROOM_TOKEN, "abcd1234")
            .putExtra(BundleKeys.KEY_FORWARD_MSG_FLAG, true)

    @Test
    fun `a shared text is passed on as text only, without the files that come with it`() =
        runBlocking {
            val intent = shareOfOtherApp("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "https://example.com/page")
                .putExtra(Intent.EXTRA_STREAM, photoOfOtherApp)
            intent.clipData = ClipData.newRawUri(null, photoOfOtherApp)

            val listIntent = checkNotNull(factory.create(intent))

            val listEntryPoint = InternalEntryPoint.nameOf(ConversationsListActivity::class.java)
            assertEquals(listEntryPoint, listIntent.component?.className)
            assertEquals(Intent.ACTION_SEND, listIntent.action)
            assertEquals(setOf(Intent.EXTRA_TEXT), listIntent.extras?.keySet())
            assertEquals("https://example.com/page", listIntent.getStringExtra(Intent.EXTRA_TEXT))
            assertNull(listIntent.clipData)
        }

    @Test
    fun `a shared file is passed on as a copy in the share directory of the app`() =
        runBlocking {
            shadowOf(context.contentResolver).registerInputStream(photoOfOtherApp, ByteArrayInputStream(PHOTO))
            val intent = shareOfOtherApp("image/jpeg").putExtra(Intent.EXTRA_STREAM, photoOfOtherApp)

            val listIntent = checkNotNull(factory.create(intent))

            assertNull(listIntent.extras)
            val copy = File(checkNotNull(listIntent.clipData).getItemAt(0).uri.path!!)
            assertEquals("photo.jpg", copy.name)
            assertEquals(true, copy.canonicalPath.startsWith(shareDirectories.canonicalPath + File.separator))
            assertEquals(PHOTO.toList(), copy.readBytes().toList())
        }

    @Test
    fun `a file of the app outside the shared caches is not passed on`() =
        runBlocking {
            val log = Uri.parse("content://${context.packageName}/logs/log.txt")
            shadowOf(context.contentResolver).registerInputStream(log, ByteArrayInputStream(PHOTO))
            val intent = shareOfOtherApp("application/octet-stream")
            intent.clipData = ClipData.newRawUri(null, log)

            assertNull(factory.create(intent))
        }

    @Test
    fun `a file uri is not passed on`() =
        runBlocking {
            val intent = shareOfOtherApp("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(context.filesDir, "secret")))

            assertNull(factory.create(intent))
        }

    @Test
    fun `the id of a direct share target is only passed on if the app published it`() =
        runBlocking {
            fun share(shortcutId: String) =
                shareOfOtherApp("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, "text")
                    .putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, shortcutId)

            assertEquals(
                PUBLISHED_SHORTCUT_ID,
                factory.create(share(PUBLISHED_SHORTCUT_ID))?.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
            )
            assertNull(factory.create(share("other"))?.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID))
        }

    @Test
    fun `a share without a file to copy leaves no directory for copies`() =
        runBlocking {
            val texts = shareOfOtherApp("text/html")
            texts.clipData = ClipData.newPlainText(null, "<b>shared</b>")
            val untrusted = shareOfOtherApp("application/octet-stream")
                .putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(context.filesDir, "secret")))

            assertEquals("<b>shared</b>", factory.create(texts)?.clipData?.getItemAt(0)?.text)
            assertNull(factory.create(untrusted))
            assertEquals(0, shareDirectories.listFiles().orEmpty().size)
        }

    @Test
    fun `a shared file is copied into a directory of its share`() =
        runBlocking {
            shadowOf(context.contentResolver).registerInputStream(photoOfOtherApp, ByteArrayInputStream(PHOTO))

            factory.create(shareOfOtherApp("image/jpeg").putExtra(Intent.EXTRA_STREAM, photoOfOtherApp))

            assertEquals(1, shareDirectories.listFiles().orEmpty().size)
        }

    @Test
    fun `a share of which no file can be read opens no conversation list`() =
        runBlocking {
            val unreadable = Uri.parse("content://com.example.gallery/photos/revoked.jpg")

            assertNull(factory.create(shareOfOtherApp("image/jpeg").putExtra(Intent.EXTRA_STREAM, unreadable)))
        }

    @Test
    fun `the files that can be read are passed on without the others`() =
        runBlocking {
            shadowOf(context.contentResolver).registerInputStream(photoOfOtherApp, ByteArrayInputStream(PHOTO))
            val unreadable = Uri.parse("content://com.example.gallery/photos/revoked.jpg")
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(unreadable, photoOfOtherApp))

            assertEquals(1, factory.create(intent)?.clipData?.itemCount)
        }

    @Test
    fun `shared streams that are not uris are left out`() =
        runBlocking {
            shadowOf(context.contentResolver).registerInputStream(photoOfOtherApp, ByteArrayInputStream(PHOTO))
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).setType("image/jpeg")
                .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf<Parcelable>(Intent(), photoOfOtherApp))

            assertEquals(1, factory.create(intent)?.clipData?.itemCount)
        }

    @Test
    fun `a share that can't be read opens no conversation list`() =
        runBlocking {
            val failingFactory = ShareListIntentFactory(
                context,
                uriOfCopy = { Uri.fromFile(it) },
                isPublishedShareTarget = { throw IllegalStateException("user is locked") }
            )
            val intent = shareOfOtherApp("text/plain")
                .putExtra(Intent.EXTRA_TEXT, "text")
                .putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, PUBLISHED_SHORTCUT_ID)

            assertNull(failingFactory.create(intent))
        }

    @Test
    fun `a cancelled share stops copying and leaves no copies behind`() =
        runBlocking {
            lateinit var share: Job
            var reads = 0
            // a file that never ends, e.g. a large video: the share is left while it is copied
            val endlessFile = object : InputStream() {
                override fun read(): Int = 0

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                    if (++reads == 1) share.cancel()
                    return length
                }
            }
            shadowOf(context.contentResolver).registerInputStream(photoOfOtherApp, endlessFile)
            val intent = shareOfOtherApp("image/jpeg").putExtra(Intent.EXTRA_STREAM, photoOfOtherApp)

            share = launch(Dispatchers.IO, start = CoroutineStart.LAZY) { factory.create(intent) }
            share.start()
            withTimeout(TIMEOUT_MS) { share.join() }

            assertTrue(share.isCancelled)
            assertEquals(1, reads)
            assertTrue(shareDirectories.listFiles().isNullOrEmpty())
        }

    companion object {
        private const val TIMEOUT_MS = 10_000L
        private const val PUBLISHED_SHORTCUT_ID = "conversation_1_abcd1234"
        private val PHOTO = byteArrayOf(1, 2, 3, 4)
    }
}
