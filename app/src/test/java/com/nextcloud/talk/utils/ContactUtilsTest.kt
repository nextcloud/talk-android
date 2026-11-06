/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ContactUtilsTest {
    @Test
    fun structuredNameIsPreferred() {
        assertEquals("Jane Doe.vcf", ContactUtils.vcfFileName("Jane Doe", "Jane"))
    }

    @Test
    fun pickedNameIsUsedWithoutStructuredName() {
        assertEquals("+49 123.vcf", ContactUtils.vcfFileName(null, "+49 123"))
        assertEquals("+49 123.vcf", ContactUtils.vcfFileName(" ", "+49 123"))
    }

    @Test
    fun contactWithoutAnyNameIsNotSavedAsNull() {
        assertEquals("contact.vcf", ContactUtils.vcfFileName(null, null))
        assertEquals("contact.vcf", ContactUtils.vcfFileName("", ""))
    }
}
