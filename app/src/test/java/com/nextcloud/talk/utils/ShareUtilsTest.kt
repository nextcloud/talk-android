/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

import android.content.Context
import android.content.res.Resources
import android.text.TextUtils
import at.bitfire.dav4jvm.HttpUtils.parseDate
import com.nextcloud.talk.R
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.utils.database.user.UserUtils
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations
import org.powermock.api.mockito.PowerMockito
import org.powermock.core.classloader.annotations.PrepareForTest
import org.powermock.modules.junit4.PowerMockRunner
import java.text.ParseException

@RunWith(PowerMockRunner::class)
@PrepareForTest(TextUtils::class)
class ShareUtilsTest {
    @Mock
    private val context: Context? = null

    @Mock
    private val resources: Resources? = null

    @Mock
    private val userUtils: UserUtils? = null

    @Mock
    private val conversation: Conversation? = null

    @Mock
    private val userEntity: UserEntity? = null
    private val baseUrl = "https://my.nextcloud.com"
    private val token = "2aotbrjr"

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        PowerMockito.mockStatic(TextUtils::class.java)
        Mockito.`when`(userUtils!!.currentUser).thenReturn(userEntity)
        Mockito.`when`(userEntity!!.baseUrl).thenReturn(baseUrl)
        Mockito.`when`(conversation!!.getToken()).thenReturn(token)
        Mockito.`when`(context!!.resources).thenReturn(resources)
        Mockito.`when`(resources!!.getString(R.string.nc_share_text))
            .thenReturn("Join the conversation at %1\$s/index.php/call/%2\$s")
        Mockito.`when`(resources.getString(R.string.nc_share_text_pass)).thenReturn("\nPassword: %1\$s")
    }

    @Test
    fun stringForIntent_noPasswordGiven_correctStringWithoutPasswordReturned() {
        PowerMockito.`when`(TextUtils.isEmpty(ArgumentMatchers.anyString())).thenReturn(true)
        val expectedResult = String.format(
            "Join the conversation at %s/index.php/call/%s",
            baseUrl, token
        )
        Assert.assertEquals(
            "Intent string was not as expected",
            expectedResult, ShareUtils.getStringForIntent(context, "", userUtils, conversation)
        )
    }

    @Test
    fun stringForIntent_passwordGiven_correctStringWithPasswordReturned() {
        PowerMockito.`when`(TextUtils.isEmpty(ArgumentMatchers.anyString())).thenReturn(false)
        val password = "superSecret"
        val expectedResult = String.format(
            "Join the conversation at %s/index.php/call/%s\nPassword: %s",
            baseUrl, token, password
        )
        Assert.assertEquals(
            "Intent string was not as expected",
            expectedResult, ShareUtils.getStringForIntent(context, password, userUtils, conversation)
        )
    }

    @Test
    @Throws(ParseException::class)
    fun date() {
        assertEquals(1207778138000, parseDate("Mon, 09 Apr 2008 23:55:38 GMT")?.time)
    }
}
