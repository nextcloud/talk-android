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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the file list being reviewed for upload and each file's (IO-derived) [FileDescription],
 * so both survive configuration changes (e.g. screen rotation) instead of resetting back to the
 * dialog's original arguments. Everything else on the screen — caption text, drag/scroll
 * position, HQ toggle animation state — is ephemeral UI state and stays in Compose's `remember`.
 */
internal class FileAttachmentPreviewViewModel @Inject constructor(private val context: Context) : ViewModel() {

    val files = mutableStateListOf<String>()

    private val _descriptionsByUri = mutableStateMapOf<String, FileDescription>()
    val descriptionsByUri: Map<String, FileDescription> get() = _descriptionsByUri

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
        viewModelScope.launch(Dispatchers.IO) {
            val described = snapshot.associateWith { describeFile(context, it, compress) }
            _descriptionsByUri.clear()
            _descriptionsByUri.putAll(described)
        }
    }
}
