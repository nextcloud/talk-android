/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.mention

import android.content.Context
import android.text.Editable
import android.text.Spanned
import android.widget.EditText
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.text.EmojiSpan
import com.nextcloud.talk.R
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem.Companion.SOURCE_FEDERATION
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.DisplayUtils
import com.nextcloud.talk.utils.text.Spans
import third.parties.fresco.BetterImageSpan

/**
 * Replaces the typed `@word` with the chosen mention, drawn as a chip.
 */
class MentionChipInserter(
    private val context: Context,
    private val conversationUser: User,
    private val editText: EditText,
    private val viewThemeUtils: ViewThemeUtils
) {
    fun insert(editable: Editable, query: MentionQuery, item: MentionAutocompleteItem) {
        val label = item.displayName.orEmpty()
        val replacement = withoutEmojiSpans(label)

        editable.replace(query.start, query.end, "$PADDING$replacement ")

        val chipSpan = Spans.MentionChipSpan(
            DisplayUtils.getDrawableForMentionChipSpan(
                context,
                item.objectId,
                item.roomToken,
                label,
                conversationUser,
                item.source.orEmpty(),
                R.xml.chip_you,
                editText,
                viewThemeUtils,
                item.source == SOURCE_FEDERATION
            ),
            BetterImageSpan.ALIGN_CENTER,
            item.mentionId ?: item.objectId,
            label
        )
        val chipStart = query.start + PADDING.length
        editable.setSpan(chipSpan, chipStart, chipStart + replacement.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /**
     * Strips the characters EmojiCompat would replace by an [EmojiSpan], as the chip draws the label itself.
     */
    private fun withoutEmojiSpans(label: String): String {
        val emojiCompat = EmojiCompat.get()
        val processed = emojiCompat.takeIf { it.loadState == EmojiCompat.LOAD_STATE_SUCCEEDED }
            ?.process(label) as? Spanned
            ?: return label
        val builder = StringBuilder(label)
        processed.getSpans(0, processed.length, EmojiSpan::class.java)
            .sortedByDescending { processed.getSpanStart(it) }
            .forEach { builder.delete(processed.getSpanStart(it), processed.getSpanEnd(it)) }
        return builder.toString()
    }

    companion object {
        private const val PADDING = " "
    }
}
