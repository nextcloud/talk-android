/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2019 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload.chunked

data class Chunk(var start: Long, var end: Long) {
    fun length(): Long = end - start + 1
}
