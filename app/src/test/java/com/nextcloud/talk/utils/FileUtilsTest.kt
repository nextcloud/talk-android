/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileUtilsTest {

    @Test
    fun resolveFileInDirectory_acceptsSimpleFileName() {
        val baseDir = createTempDir()
        try {
            val result = FileUtils.resolveFileInDirectory(baseDir, "report.pdf")

            assertNotNull(result)
            assertEquals(File(baseDir, "report.pdf").canonicalPath, result?.canonicalPath)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveFileInDirectory_rejectsTraversal() {
        val baseDir = createTempDir()
        try {
            val result = FileUtils.resolveFileInDirectory(baseDir, "../settings.preferences_pb")

            assertNull(result)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveFileInDirectory_rejectsNestedPath() {
        val baseDir = createTempDir()
        try {
            val result = FileUtils.resolveFileInDirectory(baseDir, "nested/file.txt")

            assertNull(result)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveFileInDirectory_rejectsBackslashPath() {
        val baseDir = createTempDir()
        try {
            val result = FileUtils.resolveFileInDirectory(baseDir, "nested\\file.txt")

            assertNull(result)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveFileInDirectory_rejectsBlank() {
        val baseDir = createTempDir()
        try {
            val result = FileUtils.resolveFileInDirectory(baseDir, "")

            assertNull(result)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun getSharedAttachmentsDirectory_createsDedicatedSubDirectory() {
        val baseDir = createTempDir()
        try {
            val sharedDirectory = FileUtils.getSharedAttachmentsDirectory(baseDir)

            assertNotNull(sharedDirectory)
            assertTrue(sharedDirectory!!.exists())
            assertTrue(sharedDirectory.isDirectory)
            assertEquals("shared_attachments", sharedDirectory.name)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveSharedAttachmentFile_resolvesInsideDedicatedSubDirectory() {
        val baseDir = createTempDir()
        try {
            val result = FileUtils.resolveSharedAttachmentFile(baseDir, "example.pdf")

            assertNotNull(result)
            val expected = File(baseDir, "shared_attachments/example.pdf").canonicalPath
            assertEquals(expected, result?.canonicalPath)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveSharedAttachmentFile_rejectsTraversal() {
        val baseDir = createTempDir()
        try {
            val result = FileUtils.resolveSharedAttachmentFile(baseDir, "../settings.preferences_pb")

            assertNull(result)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveSharedAttachmentFile_withFileId_resolvesInsidePerFileIdSubDirectory() {
        val baseDir = createTempDir()
        try {
            val result = FileUtils.resolveSharedAttachmentFile(baseDir, "42", "example.pdf")

            assertNotNull(result)
            val expected = File(baseDir, "shared_attachments/42/example.pdf").canonicalPath
            assertEquals(expected, result?.canonicalPath)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveSharedAttachmentFile_withoutFileId_resolvesInsideDedicatedSubDirectory() {
        val baseDir = createTempDir()
        try {
            val expected = File(baseDir, "shared_attachments/example.pdf").canonicalPath

            assertEquals(expected, FileUtils.resolveSharedAttachmentFile(baseDir, null, "example.pdf")?.canonicalPath)
            assertEquals(expected, FileUtils.resolveSharedAttachmentFile(baseDir, "", "example.pdf")?.canonicalPath)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveSharedAttachmentFile_withFileId_keepsDifferentFileIdsFromColliding() {
        val baseDir = createTempDir()
        try {
            val first = FileUtils.resolveSharedAttachmentFile(baseDir, "1", "image.jpg")
            val second = FileUtils.resolveSharedAttachmentFile(baseDir, "2", "image.jpg")

            assertNotNull(first)
            assertNotNull(second)
            assertTrue(first?.canonicalPath != second?.canonicalPath)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun resolveSharedAttachmentFile_withFileId_rejectsTraversalInFileId() {
        val baseDir = createTempDir()
        try {
            val result = FileUtils.resolveSharedAttachmentFile(baseDir, "../evil", "example.pdf")

            assertNull(result)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun getSharedAttachmentsDirectory_returnsNullWhenPathIsFile() {
        val baseDir = createTempDir()
        try {
            File(baseDir, "shared_attachments").writeText("not a directory")

            val result = FileUtils.getSharedAttachmentsDirectory(baseDir)

            assertNull(result)
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun sharedAttachmentProviderFile_resolvesFileInSharedAttachments() {
        val baseDir = createTempDir()
        try {
            val sharedAttachments = File(baseDir, "shared_attachments").canonicalFile
            assertEquals(
                File(sharedAttachments, "image.jpg"),
                FileUtils.sharedAttachmentProviderFile(baseDir, baseDir, listOf("shared_attachments", "image.jpg"))
            )
            assertEquals(
                File(sharedAttachments, "42/image.jpg"),
                FileUtils.sharedAttachmentProviderFile(
                    baseDir,
                    baseDir,
                    listOf("shared_attachments", "42", "image.jpg")
                )
            )
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun sharedAttachmentProviderFile_rejectsOtherProviderPaths() {
        val baseDir = createTempDir()
        try {
            assertNull(FileUtils.sharedAttachmentProviderFile(baseDir, baseDir, listOf("logs", "log.txt")))
            assertNull(FileUtils.sharedAttachmentProviderFile(baseDir, baseDir, listOf("photos", "photo.jpg")))
            assertNull(FileUtils.sharedAttachmentProviderFile(baseDir, baseDir, listOf("shared_attachments")))
            assertNull(FileUtils.sharedAttachmentProviderFile(baseDir, baseDir, emptyList()))
        } finally {
            baseDir.deleteRecursively()
        }
    }

    @Test
    fun sharedAttachmentProviderFile_rejectsTraversal() {
        val baseDir = createTempDir()
        try {
            val traversals = listOf(
                listOf("shared_attachments", "..", "secret.db"),
                listOf("shared_attachments", "../secret.db"),
                listOf("shared_file_copies", "..", "secret.db")
            )
            traversals.forEach { assertNull(FileUtils.sharedAttachmentProviderFile(baseDir, baseDir, it)) }
        } finally {
            baseDir.deleteRecursively()
        }
    }

    private fun createTempDir(): File = kotlin.io.path.createTempDirectory("file-utils-test-").toFile()
}
