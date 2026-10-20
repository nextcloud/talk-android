/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.remotefilebrowser.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemoteFileBrowserItemTest {

    @Test
    fun `file id is the zero-padded number in front of the instance id`() {
        assertEquals("123", RemoteFileBrowserItem(remoteId = "00000123oc1a2b3c4d5e").fileId)
    }

    @Test
    fun `file ids longer than the padding are kept in full`() {
        assertEquals("1234567890", RemoteFileBrowserItem(remoteId = "1234567890oc1a2b3c4d5e").fileId)
    }

    @Test
    fun `items without a usable remote id have no file id`() {
        assertNull(RemoteFileBrowserItem(remoteId = null).fileId)
        assertNull(RemoteFileBrowserItem(remoteId = "").fileId)
        assertNull(RemoteFileBrowserItem(remoteId = "oc1a2b3c4d5e").fileId)
    }
}
