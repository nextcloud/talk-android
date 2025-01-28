/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.view.View
import android.widget.ImageView
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.extensions.loadBotsAvatar
import com.nextcloud.talk.extensions.loadChangelogBotAvatar
import com.nextcloud.talk.extensions.loadDefaultAvatar
import com.nextcloud.talk.extensions.loadFederatedUserAvatar
import com.nextcloud.talk.extensions.loadFirstLetterAvatar
import com.nextcloud.talk.ui.theme.ViewThemeUtils

class ChatMessageUtils {

    fun setAvatarOnMessage(view: ImageView, message: ChatMessage, viewThemeUtils: ViewThemeUtils) {
        view.visibility = View.VISIBLE
        if (message.actorType == "guests" || message.actorType == "emails") {
            val actorName = message.actorDisplayName
            if (!actorName.isNullOrBlank()) {
                view.loadFirstLetterAvatar(actorName)
            } else {
                view.loadDefaultAvatar(viewThemeUtils)
            }
        } else if (message.actorType == "bots" && (message.actorId == "changelog" || message.actorId == "sample")) {
            view.loadChangelogBotAvatar()
        } else if (message.actorType == "bots") {
            view.loadBotsAvatar()
        } else if (message.actorType == "federated_users") {
            view.loadFederatedUserAvatar(message)
        }
    }
}
