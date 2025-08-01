/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.login.data.local

import org.junit.Test

class LocalLoginDataSourceTest {

    // Note Use Robolectric for testing database queries
    // might be tricky though, would this be reflective of a real app db?
    // Perhaps Mockito? Also need to check work manager jobs. Don't know much about work manager
    // what are the edge cases?

    @Test
    fun `test checkIfUserExists correct path`() {

    }

    @Test
    fun `test checkIfUserExists error path`() {

    }

    @Test
    fun `test checkIfUserIsScheduledForDeletion correct path`() {

    }

    @Test
    fun `test checkIfUserIsScheduledForDeletion error path`() {

    }

    @Test
    fun `test startAccountRemovalWorker correct path`() {

    }

    @Test
    fun `test updateUser updated path`() {

    }

    @Test
    fun `test updateUser create new user path`() {

    }
}
