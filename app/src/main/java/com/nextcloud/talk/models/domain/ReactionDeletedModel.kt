/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.domain

import com.nextcloud.talk.chat.data.model.ChatMessage

data class ReactionDeletedModel(var chatMessage: ChatMessage, var emoji: String, var success: Boolean)
