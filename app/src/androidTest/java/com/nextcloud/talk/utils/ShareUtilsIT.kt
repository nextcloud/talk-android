/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import at.bitfire.dav4jvm.HttpUtils
import org.apache.commons.lang3.time.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.util.Date
import java.util.Locale

@Ignore("Test fails on CI server. See issue https://github.com/nextcloud/talk-android/issues/1737")
class ShareUtilsIT {
    @Test
    fun date() {
        assertEquals(TEST_DATE_IN_MILLIS, parseDate2("Mon, 09 Apr 2008 23:55:38 GMT").time)
        assertEquals(TEST_DATE_IN_MILLIS, HttpUtils.parseDate("Mon, 09 Apr 2008 23:55:38 GMT")?.time)
    }

    private fun parseDate2(dateStr: String): Date =
        DateUtils.parseDate(
            dateStr, Locale.US,
            HttpUtils.httpDateFormatStr,
            // RFC 822, updated by RFC 1123 with any TZ
            "EEE, dd MMM yyyy HH:mm:ss zzz",
            // RFC 850, obsoleted by RFC 1036 with any TZ.
            "EEEE, dd-MMM-yy HH:mm:ss zzz",
            // ANSI C's asctime() format
            "EEE MMM d HH:mm:ss yyyy",
            // Alternative formats.
            "EEE, dd-MMM-yyyy HH:mm:ss z",
            "EEE, dd-MMM-yyyy HH-mm-ss z",
            "EEE, dd MMM yy HH:mm:ss z",
            "EEE dd-MMM-yyyy HH:mm:ss z",
            "EEE dd MMM yyyy HH:mm:ss z",
            "EEE dd-MMM-yyyy HH-mm-ss z",
            "EEE dd-MMM-yy HH:mm:ss z",
            "EEE dd MMM yy HH:mm:ss z",
            "EEE,dd-MMM-yy HH:mm:ss z",
            "EEE,dd-MMM-yyyy HH:mm:ss z",
            "EEE, dd-MM-yyyy HH:mm:ss z",
            // RI bug 6641315 claims a cookie of this format was once served by www.yahoo.com
            "EEE MMM d yyyy HH:mm:ss z"
        )

    companion object {
        private const val TEST_DATE_IN_MILLIS = 1207778138000
    }
}
