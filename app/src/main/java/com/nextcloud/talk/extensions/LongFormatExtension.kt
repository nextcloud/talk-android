/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.extensions

fun Long.toIntOrZero(): Int =
    if (this >= Int.MIN_VALUE && this <= Int.MAX_VALUE) {
        toInt()
    } else {
        0
    }
