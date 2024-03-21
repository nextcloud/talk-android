/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.content.Context
import android.content.res.Resources
import com.nextcloud.talk.R
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel
import com.nextcloud.talk.users.UserManager
import io.reactivex.Maybe
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

    private lateinit var conversation: ConversationModel

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        Mockito.`when`(userManager!!.currentUser).thenReturn(Maybe.just(user))
        Mockito.`when`(user!!.baseUrl).thenReturn(baseUrl)
        Mockito.`when`(context!!.resources).thenReturn(resources)
        Mockito.`when`(resources!!.getString(R.string.nc_share_text))
            .thenReturn("Join the conversation at %1\$s/index.php/call/%2\$s")
        Mockito.`when`(resources.getString(R.string.nc_share_text_pass)).thenReturn("\nPassword: %1\$s")

        conversation = ConversationModel(token = token)
    }

    @Test
    fun stringForIntent_noPasswordGiven_correctStringWithoutPasswordReturned() {
        val expectedResult = String.format(
            "Join the conversation at %s/index.php/call/%s",
            baseUrl,
            token
        )
        Assert.assertEquals(
            "Intent string was not as expected",
            expectedResult,
            ShareUtils.getStringForIntent(context!!, userManager!!.currentUser.blockingGet(), conversation)
        )
    }
}
