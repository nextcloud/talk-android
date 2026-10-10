/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UploadProgressThrottleTest {

    private var now = 0L
    private val throttle = UploadProgressThrottle(1000) { now }

    @Test
    fun `first update passes`() {
        assertTrue(throttle.shouldUpdate(1))
    }

    @Test
    fun `updates within a second are dropped`() {
        throttle.shouldUpdate(1)
        now = 999
        assertFalse(throttle.shouldUpdate(2))
    }

    @Test
    fun `update passes after a second`() {
        throttle.shouldUpdate(1)
        now = 1000
        assertTrue(throttle.shouldUpdate(2))
    }

    @Test
    fun `unchanged value is dropped`() {
        throttle.shouldUpdate(5)
        now = 5000
        assertFalse(throttle.shouldUpdate(5))
    }

    @Test
    fun `a dropped update does not restart the interval`() {
        throttle.shouldUpdate(1)
        now = 600
        throttle.shouldUpdate(2)
        now = 1000
        assertTrue(throttle.shouldUpdate(3))
    }
}
