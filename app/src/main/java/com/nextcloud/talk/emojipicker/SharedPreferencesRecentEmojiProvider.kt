/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.emojipicker

import android.content.Context
import androidx.emoji2.emojipicker.RecentEmojiProvider

class SharedPreferencesRecentEmojiProvider(
    context: Context,
    prefsName: String,
    private val maxStored: Int = MAX_STORED_RECENT_EMOJIS
) : RecentEmojiProvider {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun recordSelection(emoji: String) {
        val updated = listOf(emoji) + getStoredList().filterNot { it == emoji }
        prefs.edit()
            .putString(KEY_RECENT, updated.take(maxStored).joinToString(","))
            .apply()
    }

    override suspend fun getRecentEmojiList(): List<String> = getStoredList()

    private fun getStoredList(): List<String> =
        prefs.getString(KEY_RECENT, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    companion object {
        private const val KEY_RECENT = "recent"
        private const val MAX_STORED_RECENT_EMOJIS = 20
    }
}
