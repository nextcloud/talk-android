/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.callnotification

import android.app.Application
import android.os.Bundle
import com.nextcloud.talk.utils.bundle.BundleKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tests how CallNotificationActivity reads the id of the call notification that backs the window. The id is the push
 * timestamp cut to an Int, so every value is valid and only a missing extra means there is no notification.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class CallNotificationActivityNotificationTest {

    @Test
    fun `missing timestamp extra resolves to null`() {
        assertNull(CallNotificationActivity.notificationIdOrNull(Bundle()))
    }

    @Test
    fun `timestamp extra is used as the notification id`() {
        assertEquals(1_700_000_000, CallNotificationActivity.notificationIdOrNull(extrasWithId(1_700_000_000)))
    }

    @Test
    fun `zero and negative ids are valid`() {
        assertEquals(0, CallNotificationActivity.notificationIdOrNull(extrasWithId(0)))
        assertEquals(-42, CallNotificationActivity.notificationIdOrNull(extrasWithId(-42)))
    }

    private fun extrasWithId(id: Int) = Bundle().apply { putInt(BundleKeys.KEY_NOTIFICATION_TIMESTAMP, id) }
}
