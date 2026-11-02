/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationlist

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.nextcloud.talk.utils.FileUtils
import com.nextcloud.talk.utils.Mimetype
import com.nextcloud.talk.utils.SharedFileCopies
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Creates the intent of the conversation list in share mode for the content another app shared to [ShareActivity],
 * with the text or the copies of the shared files, or none if neither is left, e.g. when no shared file could be read.
 * The other extras of a shared intent could come from any app, so
 * only the id of a direct share target is taken over, and only if the app published it.
 *
 * [uriOfCopy] gives the uri a copy is shared with, and [isPublishedShareTarget] whether the app published a direct
 * share shortcut; both can be replaced in tests, which have neither the file provider paths nor the shortcuts.
 */
internal class ShareListIntentFactory(
    private val context: Context,
    private val uriOfCopy: (File) -> Uri = { FileProvider.getUriForFile(context, context.packageName, it) },
    private val isPublishedShareTarget: (String) -> Boolean = { DirectShareHelper.isPublishedShareTarget(context, it) }
) {

    @Suppress("Detekt.TooGenericExceptionCaught")
    suspend fun create(sharedIntent: Intent): Intent? =
        try {
            createListIntent(sharedIntent)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // the extras of another app can be malformed, e.g. a bundle that can't be read
            Log.w(TAG, "Failed to read the shared content", e)
            null
        }

    private suspend fun createListIntent(sharedIntent: Intent): Intent? {
        val listIntent = ConversationsListActivity.createIntent(context)
            .setAction(sharedIntent.action)
            .setType(sharedIntent.type)
            .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
        // Android starts a direct share as the sharing app, so any app can set a shortcut id: the list only preselects
        // the conversation of one the share sheet can show, and the chat still asks before sending the content
        val shortcutId = sharedIntent.getStringExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID)
        val isPublished = shortcutId != null && withContext(Dispatchers.IO) { isPublishedShareTarget(shortcutId) }
        if (isPublished) listIntent.putExtra(ShortcutManagerCompat.EXTRA_SHORTCUT_ID, shortcutId)

        // of a shared text only the text is passed on, e.g. not the screenshot a browser adds to a shared page: the
        // list takes the text if there is one, and the files otherwise
        val text = sharedIntent.getStringExtra(Intent.EXTRA_TEXT)
        if (sharedIntent.type == Mimetype.TEXT_PLAIN && text != null) {
            return listIntent.putExtra(Intent.EXTRA_TEXT, text)
        }
        // created with the first file that is copied: a share without one leaves no directory behind, and doesn't wait
        // for WorkManager to tell which copies of older shares can be removed
        val directoryLazy = lazy { SharedFileCopies.createDirectory(context) }
        val items = try {
            withContext(Dispatchers.IO) { copySharedItems(sharedIntent, directoryLazy) }
        } catch (e: CancellationException) {
            // also when it is cancelled right after the last file was copied
            if (directoryLazy.isInitialized()) directoryLazy.value?.deleteRecursively()
            throw e
        }
        // none without items, as the list could only show an error when a conversation is picked
        return if (items.isEmpty()) null else listIntent.apply { clipData = clipDataOf(sharedIntent.type, items) }
    }

    private fun clipDataOf(type: String?, items: List<ClipData.Item>): ClipData =
        ClipData(null, arrayOf(type ?: "*/*"), items.first()).apply { items.drop(1).forEach { addItem(it) } }

    /**
     * The shared texts, and copies of the shared files in the directory of [directoryLazy] that may be uploaded, see
     * [FileUtils.isTrustedShareUri]. Stops copying when it is cancelled, e.g. when the share is left.
     */
    private suspend fun copySharedItems(sharedIntent: Intent, directoryLazy: Lazy<File?>): List<ClipData.Item> {
        val clipData = sharedIntent.clipData
        val sharedItems = if (clipData != null) {
            (0 until clipData.itemCount).map { clipData.getItemAt(it) }
        } else {
            sharedStreams(sharedIntent).map { ClipData.Item(it) }
        }
        return sharedItems.mapIndexedNotNull { index, item ->
            currentCoroutineContext().ensureActive()
            when {
                item.uri != null ->
                    copyToShareDirectory(item.uri) { directoryLazy.value?.let { File(it, index.toString()) } }
                        ?.let { ClipData.Item(it) }
                item.text != null -> ClipData.Item(item.text)
                else -> null
            }
        }
    }

    // without clip data, e.g. if the sharing app didn't start it with Context.startActivity, or if Android couldn't
    // take the streams into it because one isn't a uri: below API 34 the list isn't checked when it's read
    private fun sharedStreams(sharedIntent: Intent): List<Uri> =
        sharedIntent.data?.let { listOf(it) }
            ?: IntentCompat.getParcelableArrayListExtra(sharedIntent, Intent.EXTRA_STREAM, Parcelable::class.java)
                ?.filterIsInstance<Uri>()
            ?: listOfNotNull(IntentCompat.getParcelableExtra(sharedIntent, Intent.EXTRA_STREAM, Uri::class.java))

    /**
     * Copies the shared file [uri] into the directory [directoryOf] gives and returns the uri of the copy, or null if
     * it can't be used.
     */
    @Suppress("Detekt.TooGenericExceptionCaught")
    private suspend fun copyToShareDirectory(uri: Uri, directoryOf: () -> File?): Uri? {
        if (!FileUtils.isTrustedShareUri(uri, context)) {
            Log.w(TAG, "Rejecting untrusted uri from share intent: $uri")
            return null
        }
        return try {
            val file = directoryOf()?.takeIf { it.mkdirs() }?.let {
                FileUtils.resolveFileInDirectory(it, FileUtils.getFileName(uri, context)) ?: File(it, FILE_NAME)
            }
            val isCopied = file != null &&
                context.contentResolver.openInputStream(uri)
                    ?.use { input -> file.outputStream().use { input.copyToCancellable(it) } } != null
            if (isCopied) uriOfCopy(file) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // the provider of another app can throw anything
            Log.w(TAG, "Failed to copy the shared file $uri", e)
            null
        }
    }

    /** Copies this stream to [output] like [InputStream.copyTo], but stops between two buffers when cancelled. */
    private suspend fun InputStream.copyToCancellable(output: OutputStream) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            output.write(buffer, 0, count)
        }
    }

    companion object {
        private val TAG = ShareListIntentFactory::class.java.simpleName

        /** The uris of the shared files the list intent of a share passes on, the copies of [SharedFileCopies]. */
        fun sharedUrisOf(listIntent: Intent): List<String> {
            val clip = listIntent.clipData ?: return emptyList()
            return (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri?.toString() }
        }
        private const val FILE_NAME = "file"
    }
}
