/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * @author Samanwith KSN
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
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
