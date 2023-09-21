/*
 * Nextcloud Talk application
 *
 * @author Samanwith KSN
 * Copyright (C) 2023 Samanwith KSN <samanwith21@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.utils

import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Test

class UriUtilsIT {

    @Test
    fun testHasHttpProtocolPrefixed() {
        val uriHttp = "http://www.example.com"
        val resultHttp = UriUtils.hasHttpProtocolPrefixed(uriHttp)
        assertTrue(resultHttp)

        val uriHttps = "https://www.example.com"
        val resultHttps = UriUtils.hasHttpProtocolPrefixed(uriHttps)
        assertTrue(resultHttps)

        val uriWithoutPrefix = "www.example.com"
        val resultWithoutPrefix = UriUtils.hasHttpProtocolPrefixed(uriWithoutPrefix)
        assertFalse(resultWithoutPrefix)
    }

    @Test
    fun testExtractInstanceInternalFileFileId() {
        assertEquals(
            "42",
            UriUtils.extractInstanceInternalFileFileId(
                "https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=42"
            )
        )
    }

    @Test
    fun testExtractInstanceInternalFileShareFileId() {
        assertEquals(
            "42",
            UriUtils.extractInstanceInternalFileShareFileId("https://cloud.nextcloud.com/f/42")
        )
    }

    @Test
    fun testIsInstanceInternalFileShareUrl() {
        assertTrue(
            UriUtils.isInstanceInternalFileShareUrl(
                "https://cloud.nextcloud.com",
                "https://cloud.nextcloud.com/f/42"
            )
        )

        assertFalse(
            UriUtils.isInstanceInternalFileShareUrl(
                "https://nextcloud.nextcloud.com",
                "https://cloud.nextcloud.com/f/42"
            )
        )

        assertFalse(
            UriUtils.isInstanceInternalFileShareUrl(
                "https://nextcloud.nextcloud.com",
                "https://cloud.nextcloud.com/f/"
            )
        )

        assertFalse(
            UriUtils.isInstanceInternalFileShareUrl(
                "https://nextcloud.nextcloud.com",
                "https://cloud.nextcloud.com/f/test123"
            )
        )
    }

    @Test
    fun testIsInstanceInternalFileUrl() {
        assertTrue(
            UriUtils.isInstanceInternalFileUrl(
                "https://cloud.nextcloud.com",
                "https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=41"
            )
        )

        assertFalse(
            UriUtils.isInstanceInternalFileUrl(
                "https://nextcloud.nextcloud.com",
                "https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=41"
            )
        )

        assertFalse(
            UriUtils.isInstanceInternalFileUrl(
                "https://nextcloud.nextcloud.com",
                "https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid=test123"
            )
        )

        assertFalse(
            UriUtils.isInstanceInternalFileUrl(
                "https://nextcloud.nextcloud.com",
                "https://cloud.nextcloud.com/apps/files/?dir=/Engineering&fileid="
            )
        )

        assertFalse(
            UriUtils.isInstanceInternalFileUrl(
                "https://cloud.nextcloud.com",
                "https://cloud.nextcloud.com/apps/files/?dir=/Engineering"
            )
        )
    }

    @Test
    fun testIsInstanceInternalFileUrlNew() {
        assertTrue(
            UriUtils.isInstanceInternalFileUrlNew(
                "https://cloud.nextcloud.com",
                "https://cloud.nextcloud.com/apps/files/files/41?dir=/"
            )
        )

        assertFalse(
            UriUtils.isInstanceInternalFileUrlNew(
                "https://nextcloud.nextcloud.com",
                "https://cloud.nextcloud.com/apps/files/files/41?dir=/"
            )
        )
    }

    @Test
    fun testExtractInstanceInternalFileFileIdNew() {
        assertEquals(
            "42",
            UriUtils.extractInstanceInternalFileFileIdNew("https://cloud.nextcloud.com/apps/files/files/42?dir=/")
        )
    }
}
