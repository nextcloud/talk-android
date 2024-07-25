/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.provider.ContactsContract

object ContactUtils {

    const val MAX_CONTACT_LIMIT = 50
    const val CACHE_MEMORY_SIZE_PERCENTAGE = 0.1
    const val CACHE_DISK_SIZE_PERCENTAGE = 0.02

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
}
