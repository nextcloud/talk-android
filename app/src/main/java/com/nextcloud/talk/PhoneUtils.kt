/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk

object PhoneUtils {
    fun isPhoneNumber(input: String?): Boolean = input?.matches(Regex("^\\+?\\d+$")) == true
}
