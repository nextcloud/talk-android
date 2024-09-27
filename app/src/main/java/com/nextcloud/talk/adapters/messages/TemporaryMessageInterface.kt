/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.adapters.messages

interface TemporaryMessageInterface {
    fun editTemporaryMessage(id: Int, newMessage: String)
    fun deleteTemporaryMessage(id: Int)
}
