/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Holds the file list being reviewed for upload, which of them are excluded from sending, and
 * each file's (IO-derived) [FileDescription], so all survive configuration changes (e.g. screen
 * rotation) instead of resetting back to the dialog's original arguments. Everything else on the
 * screen — caption text, drag/scroll position, HQ toggle animation state — is ephemeral UI state
 * and stays in Compose's `remember`.
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

    /** Files the user un-ticked; they stay in the list (and can be re-ticked) but are not sent. */
    val unselected = mutableStateListOf<String>()

    /** True while an edit (e.g. a drawing) is being written, so sending can't race it with the old file. */
    var isEditing by mutableStateOf(false)
        private set

    /** Set when an edit could not be saved; the screen reports it once and calls [editFailureShown]. */
    var editFailed by mutableStateOf(false)
        private set

    /** The drawing in progress; here (not in `remember`) so it survives rotation. */
    val drawing = DrawingSession()

    private var describeJob: Job? = null

    fun editFailureShown() {
        editFailed = false
    }

    fun toggleSelected(uri: String) {
        val updated = toggleSelection(files, unselected.toSet(), uri)
        unselected.clear()
        unselected.addAll(updated)
    }

    fun selectedFiles(): List<String> = selectedFiles(files, unselected.toSet())

    /**
     * Swaps an edited file in at the position of the original. The old description is carried over
     * right away (re-pointed at [newUri]) so the pager doesn't drop the page while the fresh
     * description is computed; [describeFiles] then refreshes size/resolution.
     */
    fun replaceFile(oldUri: String, newUri: String) {
        val updated = replaceUri(files, oldUri, newUri)
        if (updated === files) return
        files.clear()
        files.addAll(updated)
        if (unselected.remove(oldUri)) {
            unselected.add(newUri)
        }
        _descriptionsByUri[oldUri]?.let { _descriptionsByUri[newUri] = it.copy(uri = newUri) }
        _descriptionsByUri.remove(oldUri)
    }

    /** Burns the current [drawing] strokes into a copy of [description]'s image; keeps the original on failure. */
    fun saveDrawing(description: FileDescription) {
        val drawn = drawing.strokes
        drawing.clear()
        if (drawn.isEmpty()) return
        isEditing = true
        viewModelScope.launch {
            val output = withContext(Dispatchers.IO) {
                createEditOutputFile(context, description.name, description.mimeType)?.let { file ->
                    val saved = renderDrawing(
                        context,
                        description.uri.toUri(),
                        drawn,
                        file,
                        editOutputIsPng(description.mimeType)
                    )
                    (if (saved) editedFileUri(context, file) else null).also { if (it == null) file.delete() }
                }
            }
            finishEdit(description.uri, output)
        }
    }

    /** Swaps in the file uCrop wrote to [croppedFile]; reports a failure if it can't be shared. */
    fun applyCrop(sourceUri: String, croppedFile: File) {
        val uri = editedFileUri(context, croppedFile)
        if (uri == null) croppedFile.delete()
        finishEdit(sourceUri, uri)
    }

    private fun finishEdit(oldUri: String, newUri: Uri?) {
        if (newUri == null) {
            editFailed = true
        } else {
            replaceFile(oldUri, newUri.toString())
            discardIfOwnEdit(oldUri)
        }
        isEditing = false
    }

    /** An intermediate of an earlier edit is of no use once superseded; originals are never touched. */
    private fun discardIfOwnEdit(uri: String) {
        viewModelScope.launch(Dispatchers.IO) { ownEditedFile(context, uri)?.delete() }
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
        describeJob?.cancel()
        describeJob = viewModelScope.launch(Dispatchers.IO) {
            val described = snapshot.associateWith {
                ensureActive()
                describeFile(context, it, compress)
            }
            ensureActive()
            // files is mutated on the main thread, so publish there
            withContext(Dispatchers.Main) { publishDescriptions(described) }
        }
    }

    /**
     * Stores [described], keeping only files that are still in the list. A result computed before
     * [replaceFile] therefore can't wipe out the replacement's carried-over description (which would
     * drop the edited page from the pager) nor leave entries for files that are gone.
     */
    fun publishDescriptions(described: Map<String, FileDescription>) {
        val current = files.toSet()
        described.filterKeys { it in current }.forEach { (uri, description) -> _descriptionsByUri[uri] = description }
        _descriptionsByUri.keys.retainAll(current)
    }
}
