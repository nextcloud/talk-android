/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.model

data class PollDetails(
    val actorType: String?,
    val actorId: String?,
    val actorDisplayName: String?,
    val optionId: Int
)
