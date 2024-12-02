/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Christian Reiner <foss@christian-reiner.info>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages

import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.ui.PlaybackSpeed

interface VoiceMessageInterface {
    fun updateMediaPlayerProgressBySlider(message: ChatMessage, progress: Int)
    fun registerMessageToObservePlaybackSpeedPreferences(userId: String, listener: (speed: PlaybackSpeed) -> Unit)
}
