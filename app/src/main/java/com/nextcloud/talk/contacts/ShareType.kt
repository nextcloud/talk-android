/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

enum class ShareType(val shareType: String) {
    User("0"),
    Group("1"),
    Email("4"),
    Remote("5"),
    Circle("7")
}
