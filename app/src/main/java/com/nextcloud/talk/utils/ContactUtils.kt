/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object ContactUtils {
    private val TAG = ContactUtils::class.java.simpleName

    const val MAX_CONTACT_LIMIT = 50
    const val CACHE_MEMORY_SIZE_PERCENTAGE = 0.1
    const val CACHE_DISK_SIZE_PERCENTAGE = 0.02
    private const val FALLBACK_VCARD_NAME = "contact"

    fun getDisplayNameFromDeviceContact(context: Context, id: String?): String? {
        var displayName: String? = null
        val whereName =
            ContactsContract.Data.MIMETYPE +
                " = ? AND " +
                ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID +
                " = ?"
        val whereNameParams = arrayOf(ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE, id)
        val nameCursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            null,
            whereName,
            whereNameParams,
            ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME
        )
        if (nameCursor != null) {
            while (nameCursor.moveToNext()) {
                displayName =
                    nameCursor.getString(
                        nameCursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME)
                    )
            }
            nameCursor.close()
        }
        return displayName
    }

    /**
     * Copies the vCard of the picked contact [contactUri] into the shared attachment cache, off the main thread as
     * the contacts provider lives in another process. Null if the contact is gone or its name is unsafe as a file name.
     */
    suspend fun saveContactAsVcfFile(context: Context, contactUri: Uri): File? =
        withContext(Dispatchers.IO) {
            context.contentResolver.query(contactUri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID))
                val pickedName = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    .takeIf { it >= 0 }
                    ?.let { cursor.getString(it) }
                val fileName = vcfFileName(getDisplayNameFromDeviceContact(context, id), pickedName)
                val file = FileUtils.resolveSharedAttachmentFile(context.cacheDir, fileName)
                if (file == null) {
                    Log.e(TAG, "Refused to save contact with unsafe name: $fileName")
                    return@use null
                }
                writeContactToVcfFile(context, cursor, file)
                file
            }
        }

    /** The vCard file name for the first of [names] that is not blank, so a contact without a name still gets one. */
    fun vcfFileName(vararg names: String?): String =
        (names.firstOrNull { !it.isNullOrBlank() } ?: FALLBACK_VCARD_NAME) + ".vcf"

    private fun writeContactToVcfFile(context: Context, cursor: Cursor, file: File) {
        val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY))
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_VCARD_URI, lookupKey)

        val fd = context.contentResolver.openAssetFileDescriptor(uri, "r")!!
        fd.use {
            file.createNewFile()
            runCatching {
                fd.createInputStream().use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }.onFailure {
                // a truncated vCard must not be shared later
                file.delete()
            }.getOrThrow()
        }
    }
}
