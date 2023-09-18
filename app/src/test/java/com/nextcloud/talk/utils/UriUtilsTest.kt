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

import org.junit.Assert
import org.junit.Test

class UriUtilsTest {

    @Test
    fun testHasHttpProtocolPrefixed() {
        val uriHttp = "http://www.example.com"
        val resultHttp = UriUtils.hasHttpProtocollPrefixed(uriHttp)
        Assert.assertTrue(resultHttp)

        val uriHttps = "https://www.example.com"
        val resultHttps = UriUtils.hasHttpProtocollPrefixed(uriHttps)
        Assert.assertTrue(resultHttps)

        val uriWithoutPrefix = "www.example.com"
        val resultWithoutPrefix = UriUtils.hasHttpProtocollPrefixed(uriWithoutPrefix)
        Assert.assertFalse(resultWithoutPrefix)
    }
}
