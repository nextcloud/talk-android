/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.filebrowser.models.properties

import android.text.TextUtils
import android.util.Log
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils.readText
import com.nextcloud.talk.filebrowser.webdav.DavUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

class NCPermission private constructor(var ncPermission: String?) : Property {

    class Factory : PropertyFactory {
        override fun create(parser: XmlPullParser): Property {
            try {
                val text = readText(parser)
                if (!TextUtils.isEmpty(text)) {
                    return NCPermission(text)
                }
            } catch (e: IOException) {
                Log.e("NCPermission", "failed to create property", e)
            } catch (e: XmlPullParserException) {
                Log.e("NCPermission", "failed to create property", e)
            }
            return NCPermission("")
        }

        override fun getName(): Property.Name = NAME
    }

    companion object {
        @JvmField
        val NAME: Property.Name = Property.Name(DavUtils.OC_NAMESPACE, DavUtils.EXTENDED_PROPERTY_NAME_PERMISSIONS)
    }
}
