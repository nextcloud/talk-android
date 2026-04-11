/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationinfo

import androidx.annotation.StringRes

sealed class ConversationInfoUiEvent {
    data class ShowSnackbar(@StringRes val resId: Int) : ConversationInfoUiEvent()
    data class ShowSnackbarText(val text: String) : ConversationInfoUiEvent()
    data class NavigateToChat(val token: String) : ConversationInfoUiEvent()
    data object RefreshParticipants : ConversationInfoUiEvent()
}
