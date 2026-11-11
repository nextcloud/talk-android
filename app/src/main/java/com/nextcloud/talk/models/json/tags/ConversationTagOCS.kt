/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.tags

import android.os.Parcelable
import com.nextcloud.talk.models.json.generic.GenericMetaDto
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class ConversationTagOCS(var meta: GenericMetaDto? = null, var data: ConversationTagDto? = null) : Parcelable
