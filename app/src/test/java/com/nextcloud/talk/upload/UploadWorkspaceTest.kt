/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload

import com.nextcloud.talk.utils.FileUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@Suppress("TooManyFunctions")
class UploadWorkspaceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun workspace(name: String = "work") = UploadWorkspace(File(tempFolder.root, name))

    private fun create(dir: File, name: String, lastModified: Long): PreparedUpload {
        val file = File(dir, name).apply {
            writeBytes(ByteArray(SIZE))
            setLastModified(lastModified)
        }
        return PreparedUpload(file, name)
    }

    @Test
    fun `file is prepared once and reused by later runs`() {
        var calls = 0
        val first = workspace().prepareOnce { dir -> calls++.let { create(dir, "photo.jpg", 1_000) } }
        val second = workspace().prepareOnce { dir -> calls++.let { create(dir, "photo.jpg", 2_000) } }

        assertEquals(1, calls)
        assertEquals(first, second)
    }

    @Test
    fun `chunk folder key stays the same between runs`() {
        val first = workspace().prepareOnce { create(it, "video.mp4", 1_000) }!!
        val keyBefore = FileUtils.md5Sum(first.file)
        val second = workspace().prepareOnce { create(it, "video.mp4", 9_000) }!!

        assertEquals(keyBefore, FileUtils.md5Sum(second.file))
    }

    @Test
    fun `a new preparation of the same file gets another chunk folder key`() {
        val first = workspace("a").prepareOnce { create(it, "video.mp4", 1_000) }!!
        val second = workspace("b").prepareOnce { create(it, "video.mp4", 2_000) }!!

        assertTrue(FileUtils.md5Sum(first.file) != FileUtils.md5Sum(second.file))
    }

    @Test
    fun `file is prepared again when it disappeared`() {
        val first = workspace().prepareOnce { create(it, "photo.jpg", 1_000) }!!
        first.file.delete()
        var calls = 0
        val second = workspace().prepareOnce { dir -> calls++.let { create(dir, "photo.jpg", 3_000) } }

        assertEquals(1, calls)
        assertNotNull(second)
        assertTrue(second!!.file.isFile)
    }

    @Test
    fun `leftovers of an interrupted preparation are removed before the next one`() {
        val ws = workspace()
        ws.prepareOnce { dir ->
            File(dir, "half.jpg").writeText("partial")
            null
        }
        ws.prepareOnce { dir ->
            assertEquals(emptyList<String>(), dir.list()!!.toList())
            create(dir, "photo.jpg", 1_000)
        }
    }

    @Test
    fun `server errors are counted across runs`() {
        assertEquals(0, workspace().serverErrors())
        assertEquals(1, workspace().registerServerError())
        assertEquals(2, workspace().registerServerError())
        assertEquals(2, workspace().serverErrors())
    }

    @Test
    fun `server error count survives a new preparation`() {
        workspace().registerServerError()
        workspace().prepareOnce { create(it, "photo.jpg", 1_000) }

        assertEquals(1, workspace().serverErrors())
    }

    @Test
    fun `delete removes the file and the counters`() {
        val ws = workspace()
        val prepared = ws.prepareOnce { create(it, "photo.jpg", 1_000) }!!
        ws.registerServerError()
        ws.delete()

        assertFalse(prepared.file.exists())
        assertEquals(0, workspace().serverErrors())
        assertNull(workspace().prepareOnce { null })
    }

    @Test
    fun `workspaces of finished uploads are removed and living ones kept`() {
        workspace("finished").prepareOnce { create(it, "a.jpg", 1_000) }
        workspace("waiting-for-network").prepareOnce { create(it, "b.jpg", 1_000) }

        UploadWorkspace.deleteFinished(tempFolder.root) { it == "waiting-for-network" }

        assertFalse(File(tempFolder.root, "finished").exists())
        assertTrue(File(tempFolder.root, "waiting-for-network").exists())
    }

    @Test
    fun `a locked workspace is not removed even when its work is unknown`() {
        val running = workspace("running")
        running.prepareOnce { create(it, "a.jpg", 1_000) }
        assertTrue(running.tryLock())

        UploadWorkspace.deleteFinished(tempFolder.root) { false }

        assertTrue(File(tempFolder.root, "running").exists())
        running.unlock()
        UploadWorkspace.deleteFinished(tempFolder.root) { false }
        assertFalse(File(tempFolder.root, "running").exists())
    }

    @Test
    fun `only one run can hold the lock of a workspace`() {
        val first = workspace()
        val second = workspace()

        assertTrue(first.tryLock())
        assertFalse(second.tryLock())
        first.unlock()
        assertTrue(second.tryLock())
        second.unlock()
    }

    @Test
    fun `the uploaded and shared stages are kept between runs`() {
        val ws = workspace()
        assertNull(ws.uploadedPath())
        assertFalse(ws.isShared())

        ws.markUploaded("/Talk/video.mp4", "video_compressed.mp4")
        assertEquals("/Talk/video.mp4", workspace().uploadedPath())
        assertEquals("video_compressed.mp4", workspace().uploadedName())
        assertFalse(workspace().isShared())

        ws.markShared()
        assertTrue(workspace().isShared())
    }

    @Test
    fun `the stages survive a new preparation after the prepared file is gone`() {
        val ws = workspace()
        val prepared = ws.prepareOnce { create(it, "a.jpg", 1_000) }!!
        ws.markUploaded("/Talk/a.jpg", "a.jpg")
        ws.markShared()
        ws.markRestarted()
        prepared.file.delete()

        workspace().prepareOnce { create(it, "a.jpg", 2_000) }

        assertEquals("/Talk/a.jpg", workspace().uploadedPath())
        assertTrue(workspace().isShared())
        assertTrue(workspace().isRestarted())
    }

    @Test
    fun `the upload id stays the same on every run`() {
        val id = workspace().uploadId()

        assertEquals(id, workspace().uploadId())
    }

    @Test
    fun `a cancel flag can be set before the work ran and survives a new preparation`() {
        val ws = workspace()
        assertFalse(ws.isCancelled())

        ws.markCancelled()
        ws.prepareOnce { create(it, "a.jpg", 1_000) }

        assertTrue(workspace().isCancelled())
    }

    @Test
    fun `the stored prepared file is available without preparing`() {
        assertNull(workspace().prepared())

        val prepared = workspace().prepareOnce { create(it, "a.jpg", 1_000) }

        assertEquals(prepared, workspace().prepared())
    }

    companion object {
        private const val SIZE = 16
    }
}
