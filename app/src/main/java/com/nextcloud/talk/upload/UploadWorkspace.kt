/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.util.UUID

/** The file an upload sends and the name it gets on the server. */
data class PreparedUpload(val file: File, val fileName: String)

/**
 * Holds what one upload needs across runs of its worker: the prepared file (copy of the content uri, compressed
 * media), the number of server errors, the stages already done (uploaded, shared), the cancel flag and a lock
 * that keeps two runs of the same work and the cleanup of dead workspaces apart.
 *
 * The prepared file is created once. Its name, size and modification time stay the same on every run, so the
 * chunk folder on the server, which is keyed by them, stays the same too.
 */
@Suppress("TooManyFunctions")
class UploadWorkspace(private val dir: File) {

    private val preparedFile = File(dir, PREPARED_FILE)
    private val serverErrorsFile = File(dir, SERVER_ERRORS_FILE)
    private val uploadedFile = File(dir, UPLOADED_FILE)
    private val sharedFile = File(dir, SHARED_FILE)
    private val cancelledFile = File(dir, CANCELLED_FILE)
    private val uploadIdFile = File(dir, UPLOAD_ID_FILE)
    private val lockFile = File(dir, LOCK_FILE)
    private val restartedFile = File(dir, RESTARTED_FILE)

    private var lockChannel: RandomAccessFile? = null
    private var lock: FileLock? = null

    /**
     * Takes the lock of this workspace. WorkManager does not interrupt a worker thread it stopped, so the old run
     * can still be busy when a new run of the same work starts. Returns false when somebody else holds the lock.
     *
     * The lock protects only inside this process: within one process a [FileLock] is refused with
     * [OverlappingFileLockException], and that is what this relies on. Do not rely on it between processes.
     */
    @Synchronized
    fun tryLock(): Boolean {
        if (lock == null) {
            dir.mkdirs()
            val channel = runCatching { RandomAccessFile(lockFile, "rw") }.getOrNull()
            val acquired = channel?.let { acquire(it) }
            if (acquired == null) {
                channel?.close()
            } else {
                lockChannel = channel
                lock = acquired
            }
        }
        return lock != null
    }

    private fun acquire(channel: RandomAccessFile): FileLock? =
        try {
            channel.channel.tryLock()
        } catch (e: OverlappingFileLockException) {
            // The lock is held by another workspace object of this process.
            null
        } catch (e: IOException) {
            null
        }

    @Synchronized
    fun unlock() {
        runCatching { lock?.release() }
        runCatching { lockChannel?.close() }
        lock = null
        lockChannel = null
    }

    /**
     * Returns the prepared file of an earlier run, or runs [prepare] once and keeps its result.
     * [prepare] gets an empty directory it may create files in, and returns null when the file cannot be prepared.
     */
    fun prepareOnce(prepare: (File) -> PreparedUpload?): PreparedUpload? {
        stored()?.let { return it }
        resetFiles()
        dir.mkdirs()
        return prepare(dir)?.also { write(preparedFile, "${it.file.absolutePath}\n${it.fileName}") }
    }

    fun serverErrors(): Int = readLong(serverErrorsFile)?.toInt() ?: 0

    fun registerServerError(): Int {
        val count = serverErrors() + 1
        dir.mkdirs()
        write(serverErrorsFile, count.toString())
        return count
    }

    /** The prepared file of an earlier run, if there is one. */
    fun prepared(): PreparedUpload? = stored()

    /** The path on the server the file was uploaded to, or null while the upload is not complete. */
    fun uploadedPath(): String? = uploadedLines()?.getOrNull(0)?.takeIf { it.isNotEmpty() }

    /** The name the uploaded file was shared under, kept because the prepared file may be gone by then. */
    fun uploadedName(): String? = uploadedLines()?.getOrNull(1)?.takeIf { it.isNotEmpty() }

    fun markUploaded(path: String, fileName: String) {
        dir.mkdirs()
        write(uploadedFile, "$path\n$fileName")
    }

    private fun uploadedLines(): List<String>? = runCatching { uploadedFile.readLines() }.getOrNull()

    /** Whether the parts were already removed once after the server rejected the assembly. */
    fun isRestarted(): Boolean = restartedFile.exists()

    fun markRestarted() {
        dir.mkdirs()
        write(restartedFile, "1")
    }

    fun isShared(): Boolean = sharedFile.exists()

    fun markShared() {
        dir.mkdirs()
        write(sharedFile, "1")
    }

    /** Stays the same on every run, so the target of the assembled file in the draft folder does not change. */
    fun uploadId(): String {
        runCatching { uploadIdFile.readText() }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
        val id = UUID.randomUUID().toString()
        dir.mkdirs()
        write(uploadIdFile, id)
        return id
    }

    /** Asks the work to abort when it runs. Creates the directory, because the work may not have run yet. */
    fun markCancelled() {
        dir.mkdirs()
        write(cancelledFile, "1")
    }

    fun isCancelled(): Boolean = cancelledFile.exists()

    /** Call only while holding the lock. */
    fun delete() {
        dir.deleteRecursively()
    }

    private fun stored(): PreparedUpload? {
        val lines = runCatching { preparedFile.readLines() }.getOrNull()
        val file = lines?.getOrNull(0)?.let(::File)
        val name = lines?.getOrNull(1)
        return if (file != null && name != null && file.isFile) PreparedUpload(file, name) else null
    }

    private fun resetFiles() {
        dir.listFiles()?.filter { it.name !in KEPT_FILES }?.forEach {
            it.deleteRecursively()
        }
    }

    private fun readLong(file: File): Long? = runCatching { file.readText().trim().toLong() }.getOrNull()

    private fun write(file: File, text: String) {
        val tmp = File(dir, file.name + TMP_SUFFIX)
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    companion object {
        private const val PREPARED_FILE = "prepared"
        private const val SERVER_ERRORS_FILE = "server_errors"
        private const val UPLOADED_FILE = "uploaded"
        private const val SHARED_FILE = "shared"
        private const val CANCELLED_FILE = "cancelled"
        private const val UPLOAD_ID_FILE = "upload_id"
        private const val LOCK_FILE = "lock"
        private const val RESTARTED_FILE = "restarted"
        private const val TMP_SUFFIX = ".tmp"

        /** Not part of the prepared file: survive a new preparation. */
        private val KEPT_FILES = setOf(
            SERVER_ERRORS_FILE,
            CANCELLED_FILE,
            LOCK_FILE,
            UPLOAD_ID_FILE,
            UPLOADED_FILE,
            SHARED_FILE,
            RESTARTED_FILE
        )

        /**
         * Removes the workspaces of uploads that are no longer alive, e.g. left behind by a killed process.
         * [isAlive] gets the name of a workspace directory, which is the id of its work. A workspace whose lock is
         * held belongs to a running work and is left alone.
         */
        fun deleteFinished(root: File, isAlive: (String) -> Boolean) {
            root.listFiles()?.filter { it.isDirectory }?.forEach { deleteIfDead(it, isAlive) }
        }

        private fun deleteIfDead(dir: File, isAlive: (String) -> Boolean) {
            val workspace = UploadWorkspace(dir)
            if (!workspace.tryLock()) {
                return
            }
            try {
                if (!isAlive(dir.name)) {
                    workspace.delete()
                }
            } finally {
                workspace.unlock()
            }
        }
    }
}
