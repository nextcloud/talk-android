/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.net.toUri
import com.nextcloud.talk.utils.FileUtils
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Holds the file list being reviewed for upload and each file's (IO-derived) [FileDescription],
 * so both survive configuration changes (e.g. screen rotation) instead of resetting back to the
 * dialog's original arguments. Everything else on the screen — caption text, drag/scroll
 * position, HQ toggle animation state — is ephemeral UI state and stays in Compose's `remember`.
 */
internal class FileAttachmentPreviewViewModel(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val describer: (Context, String, Boolean) -> FileDescription = ::describeFile,
    private val mimeTypeResolver: (Context, String) -> String? = { ctx, uri ->
        FileUtils.resolveMimeType(ctx, uri.toUri())
    }
) : ViewModel() {

    // Dagger cannot use the default values above, so it gets a constructor with the context only
    @Inject
    constructor(context: Context) : this(context, ioDispatcher = Dispatchers.IO)

    val files = mutableStateListOf<String>()

    private val _descriptionsByUri = mutableStateMapOf<String, FileDescription>()
    val descriptionsByUri: Map<String, FileDescription> get() = _descriptionsByUri

    private val _mimeTypesByUri = mutableStateMapOf<String, String?>()

    /** Cheap, type-only counterpart of [descriptionsByUri]; filled before the (slow) full descriptions. */
    val mimeTypesByUri: Map<String, String?> get() = _mimeTypesByUri

    private var describeJob: Job? = null
    private var mimeTypeJob: Job? = null

    /** No-op after the first call, so re-entering (e.g. after rotation) doesn't wipe edits made since. */
    fun setInitialFiles(initialFiles: List<String>) {
        if (files.isEmpty()) {
            files.addAll(initialFiles)
        }
    }

    fun addFiles(newFiles: List<String>) {
        files.addAll(newFiles.filterNot { it in files })
    }

    fun removeFile(uri: String) {
        files.remove(uri)
    }

    fun reorder(from: Int, to: Int) {
        if (from != to && from in files.indices && to in files.indices) {
            val item = files.removeAt(from)
            files.add(to, item)
        }
    }

    /**
     * Re-describes every current file. Callers should only invoke this when the *set* of files or
     * [compress] changes — not on pure reordering — since it always redescribes the whole list.
     */
    fun describeFiles(compress: Boolean) {
        val snapshot = files.toList()
        // a stale job finishing last would retainAll away the description of a file added since
        describeJob?.cancel()
        describeJob = viewModelScope.launch(ioDispatcher) {
            val described = snapshot.associateWith { describer(context, it, compress) }
            // the blocking copy above cannot be interrupted, so a cancelled job reaches this point too;
            // withContext checks for cancellation and runs on main, where cancel() is also called
            withContext(mainDispatcher) {
                _descriptionsByUri.putAll(described)
                _descriptionsByUri.keys.retainAll(described.keys)
            }
        }
    }

    /**
     * Resolves only the MIME type of every current file. Unlike [describeFiles] this does not copy
     * content:// files into the cache, so it is quick enough not to hold anything back.
     */
    fun resolveMimeTypes() {
        val snapshot = files.toList()
        mimeTypeJob?.cancel()
        mimeTypeJob = viewModelScope.launch(ioDispatcher) {
            val resolved = snapshot.associateWith { mimeTypeResolver(context, it) }
            withContext(mainDispatcher) {
                _mimeTypesByUri.putAll(resolved)
                _mimeTypesByUri.keys.retainAll(resolved.keys)
            }
        }
    }
}
