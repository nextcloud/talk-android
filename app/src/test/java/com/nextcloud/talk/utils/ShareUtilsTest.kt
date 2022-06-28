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
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.users.UserManager
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class ShareUtilsTest {
    @Mock
    private val context: Context? = null

    @Mock
    private val resources: Resources? = null

    @Mock
    private val userManager: UserManager? = null

    @Mock
    private val user: User? = null

    private val baseUrl = "https://my.nextcloud.com"
    private val token = "2aotbrjr"

    private lateinit var conversation: Conversation

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Mockito.`when`(userManager!!.currentUser.blockingGet()).thenReturn(user)
        Mockito.`when`(user!!.baseUrl).thenReturn(baseUrl)
        Mockito.`when`(context!!.resources).thenReturn(resources)
        Mockito.`when`(resources!!.getString(R.string.nc_share_text))
            .thenReturn("Join the conversation at %1\$s/index.php/call/%2\$s")
        Mockito.`when`(resources.getString(R.string.nc_share_text_pass)).thenReturn("\nPassword: %1\$s")

        conversation = Conversation(token = token)
    }

    @Test
    fun stringForIntent_noPasswordGiven_correctStringWithoutPasswordReturned() {
        val expectedResult = String.format(
            "Join the conversation at %s/index.php/call/%s",
            baseUrl, token
        )
        Assert.assertEquals(
            "Intent string was not as expected",
            expectedResult, ShareUtils.getStringForIntent(context, "", userManager!!, conversation)
        )
    }

    @Test
    fun stringForIntent_passwordGiven_correctStringWithPasswordReturned() {
        val password = "superSecret"
        val expectedResult = String.format(
            "Join the conversation at %s/index.php/call/%s\nPassword: %s",
            baseUrl, token, password
        )
        Assert.assertEquals(
            "Intent string was not as expected",
            expectedResult, ShareUtils.getStringForIntent(context, password, userManager!!, conversation)
        )
    }
}
