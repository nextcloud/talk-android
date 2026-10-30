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
import kotlinx.coroutines.withContext
import java.io.File

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
        // none without items, as the list could only show an error when a conversation is picked
        val items = withContext(Dispatchers.IO) { copySharedItems(sharedIntent) }
        return if (items.isEmpty()) null else listIntent.apply { clipData = clipDataOf(sharedIntent.type, items) }
    }

    private fun clipDataOf(type: String?, items: List<ClipData.Item>): ClipData =
        ClipData(null, arrayOf(type ?: "*/*"), items.first()).apply { items.drop(1).forEach { addItem(it) } }

    /** The shared texts, and copies of the shared files that may be uploaded, see [FileUtils.isTrustedShareUri]. */
    private fun copySharedItems(sharedIntent: Intent): List<ClipData.Item> {
        // created with the first file that is copied: a share without one leaves no directory behind, and doesn't wait
        // for WorkManager to tell which copies of older shares can be removed
        val directory by lazy { SharedFileCopies.createDirectory(context) }
        val clipData = sharedIntent.clipData
        val sharedItems = if (clipData != null) {
            (0 until clipData.itemCount).map { clipData.getItemAt(it) }
        } else {
            sharedStreams(sharedIntent).map { ClipData.Item(it) }
        }
        return sharedItems.mapIndexedNotNull { index, item ->
            when {
                item.uri != null ->
                    copyToCache(item.uri) { directory?.let { File(it, index.toString()) } }?.let { ClipData.Item(it) }
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
    private fun copyToCache(uri: Uri, directoryOf: () -> File?): Uri? {
        if (!FileUtils.isTrustedShareUri(uri, context.packageName, context.cacheDir)) {
            Log.w(TAG, "Rejecting untrusted uri from share intent: $uri")
            return null
        }
        return try {
            val file = directoryOf()?.takeIf { it.mkdirs() }?.let {
                FileUtils.resolveFileInDirectory(it, FileUtils.getFileName(uri, context)) ?: File(it, FILE_NAME)
            }
            val isCopied = file != null &&
                context.contentResolver.openInputStream(uri)
                    ?.use { input -> file.outputStream().use { input.copyTo(it) } } != null
            if (isCopied) uriOfCopy(file) else null
        } catch (e: Exception) {
            // the provider of another app can throw anything
            Log.w(TAG, "Failed to copy the shared file $uri", e)
            null
        }
    }

    companion object {
        private val TAG = ShareListIntentFactory::class.java.simpleName
        private const val FILE_NAME = "file"
    }
}
