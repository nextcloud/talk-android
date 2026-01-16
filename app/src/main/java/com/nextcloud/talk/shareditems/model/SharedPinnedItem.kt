/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.shareditems.model

data class SharedPinnedItem(
    override val id: String,
    override val name: String,
    override val actorId: String,
    override val actorName: String,
    override val dateTime: String
) : SharedItem
