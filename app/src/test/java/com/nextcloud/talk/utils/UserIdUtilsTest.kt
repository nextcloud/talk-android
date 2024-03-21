/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Samanwith KSN <samanwith21@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.data.user.model.User
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito
import org.mockito.MockitoAnnotations

class UserIdUtilsTest {

    @Mock
    private lateinit var user: User

    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
    }

    @Test
    fun testGetIdForUser_if_userIsNull_returnsNoId() {
        Mockito.`when`(user.id).thenReturn(null)
        val result = UserIdUtils.getIdForUser(user)
        Assert.assertEquals("The id is NO_ID when user is null", UserIdUtils.NO_ID, result)
    }

    @Test
    fun testGetIdForUser_if_userIdIsSet_returnsUserId() {
        val expectedId: Long = 12345
        Mockito.`when`(user.id).thenReturn(expectedId)
        val result = UserIdUtils.getIdForUser(user)
        Assert.assertEquals("The id is correct user id is not null", expectedId, result)
    }
}
