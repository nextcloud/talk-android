package com.nextcloud.talk.utils

import android.content.Context
import android.provider.ContactsContract

object ContactUtils {

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
