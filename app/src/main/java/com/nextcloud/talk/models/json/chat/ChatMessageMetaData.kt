/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.models.json.chat

import android.os.Parcelable
import com.bluelinelabs.logansquare.annotation.JsonField
import com.bluelinelabs.logansquare.annotation.JsonObject
import kotlinx.parcelize.Parcelize

@Parcelize
@JsonObject
data class ChatMessageMetaData(
    @JsonField(name = ["pinnedActorType"]) var pinnedActorType: String? = null,
    @JsonField(name = ["pinnedActorId"]) var pinnedActorId: String? = null,
    @JsonField(name = ["pinnedActorDisplayName"]) var pinnedActorDisplayName: String? = null,
    @JsonField(name = ["pinnedAt"]) var pinnedAt: Long? = null,
    @JsonField(name = ["pinnedUntil"]) var pinnedUntil: Long? = null
) : Parcelable
