/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.emojipicker

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException

@Serializable
data class EmojiKeywordEntry(val emoji: String, val name: String, val keywords: List<String>)

/**
 * Looks up emoji by keyword against a dataset bundled in assets/emoji_keywords.json.
 * The androidx emoji2-emojipicker library used for the emoji pickers throughout the app
 * has no search API and its own emoji dataset is not accessible from app code, so this
 * ships its own keyword list (derived from the Unicode CLDR emoji short names).
 */
class EmojiKeywordProvider(private val context: Context) {

    private var cachedEntries: List<EmojiKeywordEntry>? = null

    suspend fun search(query: String, limit: Int = MAX_RESULTS): List<String> {
        val terms = query.trim().lowercase().split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
        if (terms.isEmpty()) {
            return emptyList()
        }

        val entries = getEntries()
        return entries.asSequence()
            .filter { entry -> terms.all { term -> entry.keywords.any { it.startsWith(term) } } }
            .map { it.emoji }
            .take(limit)
            .toList()
    }

    private suspend fun getEntries(): List<EmojiKeywordEntry> =
        cachedEntries ?: withContext(Dispatchers.IO) {
            cachedEntries ?: loadEntries().also { cachedEntries = it }
        }

    private fun loadEntries(): List<EmojiKeywordEntry> =
        try {
            val json = context.assets.open(ASSET_FILE_NAME).bufferedReader(Charsets.UTF_8).use { it.readText() }
            Json.decodeFromString<List<EmojiKeywordEntry>>(json)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read emoji keyword dataset", e)
            emptyList()
        } catch (e: SerializationException) {
            Log.e(TAG, "Failed to parse emoji keyword dataset", e)
            emptyList()
        }

    companion object {
        private const val TAG = "EmojiKeywordProvider"
        private const val ASSET_FILE_NAME = "emoji_keywords.json"
        private const val MAX_RESULTS = 100
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}
