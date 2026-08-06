/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

data class ManageConversationTagsCallbacks(
    val onCreateTag: (String) -> Unit,
    val onRenameTag: (String, String) -> Unit,
    val onDeleteTag: (String) -> Unit,
    val onMoveTag: (String, Int) -> Unit,
    val onResetActionState: () -> Unit
)
