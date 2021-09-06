package com.nextcloud.talk.utils

import at.bitfire.dav4jvm.HttpUtils
import org.apache.commons.lang3.time.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date
import java.util.Locale

class ShareUtilsIT {
    @Test
    fun date() {
        assertEquals(1207778138000, parseDate2("Mon, 09 Apr 2008 23:55:38 GMT").time)
        assertEquals(1207778138000, HttpUtils.parseDate("Mon, 09 Apr 2008 23:55:38 GMT")?.time)
    }

    private fun parseDate2(dateStr: String): Date {
        return DateUtils.parseDate(
            dateStr, Locale.US,
            HttpUtils.httpDateFormatStr,
            "EEE, dd MMM yyyy HH:mm:ss zzz", // RFC 822, updated by RFC 1123 with any TZ
            "EEEE, dd-MMM-yy HH:mm:ss zzz", // RFC 850, obsoleted by RFC 1036 with any TZ.
            "EEE MMM d HH:mm:ss yyyy", // ANSI C's asctime() format
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
            /* RI bug 6641315 claims a cookie of this format was once served by www.yahoo.com */
            "EEE MMM d yyyy HH:mm:ss z"
        )
    }
}
