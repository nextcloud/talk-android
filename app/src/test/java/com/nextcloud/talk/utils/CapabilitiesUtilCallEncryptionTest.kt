/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import com.nextcloud.talk.models.json.capabilities.SpreedCapabilityDto
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilitiesUtilCallEncryptionTest {

    private fun capabilities(feature: Boolean, config: Any?): SpreedCapabilityDto =
        SpreedCapabilityDto().apply {
            features = if (feature) listOf("call-end-to-end-encryption") else emptyList()
            this.config =
                hashMapOf("call" to hashMapOf<String, Any>().apply { config?.let { put("end-to-end-encryption", it) } })
        }

    @Test
    fun enabledWithFeatureAndConfig() {
        assertTrue(CapabilitiesUtil.isCallEndToEndEncryptionEnabled(capabilities(feature = true, config = true)))
    }

    @Test
    fun disabledWithoutFeature() {
        assertFalse(CapabilitiesUtil.isCallEndToEndEncryptionEnabled(capabilities(feature = false, config = true)))
    }

    @Test
    fun disabledWithoutConfig() {
        assertFalse(CapabilitiesUtil.isCallEndToEndEncryptionEnabled(capabilities(feature = true, config = false)))
        assertFalse(CapabilitiesUtil.isCallEndToEndEncryptionEnabled(capabilities(feature = true, config = null)))
        assertFalse(CapabilitiesUtil.isCallEndToEndEncryptionEnabled(null))
    }
}
