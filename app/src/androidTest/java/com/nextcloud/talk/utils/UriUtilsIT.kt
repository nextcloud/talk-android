/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Samanwith KSN <samanwith21@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
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
