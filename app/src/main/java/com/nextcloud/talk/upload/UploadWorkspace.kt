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

/** The file an upload sends and the name it gets on the server. */
data class PreparedUpload(val file: File, val fileName: String)

/**
 * Holds what one upload needs across runs of its worker: the prepared file (copy of the content uri, compressed
 * media), whether the parts on the server were already removed once, and a lock that keeps two runs of the same
 * work and the cleanup of dead workspaces apart.
 *
 * The prepared file is created once. Its name, size and modification time stay the same on every run, so the
 * chunk folder on the server, which is keyed by them, stays the same too.
 */
class UploadWorkspace(private val dir: File) {

    private val preparedFile = File(dir, PREPARED_FILE)
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

    /** Whether the parts were already removed once after the server rejected the assembly. */
    fun isRestarted(): Boolean = restartedFile.exists()

    fun markRestarted() {
        dir.mkdirs()
        write(restartedFile, "1")
    }

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

    private fun write(file: File, text: String) {
        val tmp = File(dir, file.name + TMP_SUFFIX)
        tmp.writeText(text)
        tmp.renameTo(file)
    }

    companion object {
        private const val PREPARED_FILE = "prepared"
        private const val LOCK_FILE = "lock"
        private const val RESTARTED_FILE = "restarted"
        private const val TMP_SUFFIX = ".tmp"

        /** Not part of the prepared file: survive a new preparation. */
        private val KEPT_FILES = setOf(LOCK_FILE, RESTARTED_FILE)

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
