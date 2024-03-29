/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import com.nextcloud.talk.ui.bottom.sheet.ProfileBottomSheet

data class MessagePayload(
    var roomToken: String,
    val isOwnerOrModerator: Boolean?,
    val profileBottomSheet: ProfileBottomSheet
)
