/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Application
import com.nextcloud.talk.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class SharedFileCopiesTest {

    private val provider = "content://${BuildConfig.APPLICATION_ID}"

    @Test
    fun copyOfShareIsTaggedWithItsShareDirectory() {
        assertEquals(
            "shared_file_copy_share1",
            SharedFileCopies.uploadTagOf("$provider/shared_file_copies/share1/0/photo.jpg")
        )
    }

    @Test
    fun otherFilesAreNotTagged() {
        assertNull(SharedFileCopies.uploadTagOf("$provider/shared_attachments/photo.jpg"))
        assertNull(SharedFileCopies.uploadTagOf("$provider/shared_attachments/shares/share1/0/photo.jpg"))
        assertNull(SharedFileCopies.uploadTagOf("$provider/shared_file_copies/share1"))
        assertNull(SharedFileCopies.uploadTagOf("$provider/photos/share1/0/photo.jpg"))
        assertNull(SharedFileCopies.uploadTagOf("content://other.app/shared_file_copies/share1/0/photo.jpg"))
        assertNull(SharedFileCopies.uploadTagOf("file:///shared_file_copies/share1/0/photo.jpg"))
    }
}
