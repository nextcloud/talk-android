/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.mention

import android.content.Context
import android.util.Log
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MentionSuggestionsLoader(
    private val context: Context,
    private val ncApiCoroutines: NcApiCoroutines,
    private val user: User,
    private val roomToken: String,
    private val chatApiVersion: Int
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun load(query: String): List<MentionAutocompleteItem> {
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val mentions = withContext(Dispatchers.IO) {
                    ncApiCoroutines.getMentionAutocompleteSuggestions(
                        user.getCredentials(),
                        ApiUtils.getUrlForMentionSuggestions(chatApiVersion, user.baseUrl, roomToken),
                        query,
                        LIMIT,
                        mapOf("includeStatus" to "true")
                    ).ocs?.data.orEmpty()
                }
                return mentions.map { MentionAutocompleteItem(it, context, roomToken) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (attempt == MAX_ATTEMPTS - 1) {
                    Log.e(TAG, "failed to get mention suggestions", e)
                }
            }
        }
        return emptyList()
    }

    companion object {
        private const val TAG = "MentionSuggestions"
        private const val LIMIT = 5
        private const val MAX_ATTEMPTS = 4
    }
}
