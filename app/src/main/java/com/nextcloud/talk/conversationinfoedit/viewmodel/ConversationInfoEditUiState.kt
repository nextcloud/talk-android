/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.conversationinfoedit.viewmodel

import androidx.annotation.StringRes
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.ConversationModel

data class ConversationInfoEditUiState(
    val conversationName: String = "",
    val conversationDescription: String = "",
    val conversation: ConversationModel? = null,
    val conversationUser: User? = null,
    val avatarRefreshKey: Int = 0,
    val nameEnabled: Boolean = true,
    val descriptionEnabled: Boolean = true,
    val descriptionMaxLength: Int = DESCRIPTION_MAX_LENGTH_DEFAULT,
    val isDescriptionEndpointAvailable: Boolean = false,
    val isLoading: Boolean = false,
    @param:StringRes val userMessage: Int? = null,
    val navigateBack: Boolean = false
) {
    companion object {
        const val DESCRIPTION_MAX_LENGTH_DEFAULT = 500
    }
}
