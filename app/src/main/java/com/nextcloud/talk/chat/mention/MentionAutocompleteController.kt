/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat.mention

import android.text.Editable
import android.text.Selection
import android.text.SpanWatcher
import android.text.Spannable
import android.text.Spanned
import android.text.TextWatcher
import android.widget.EditText
import com.nextcloud.talk.adapters.items.MentionAutocompleteItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MentionSuggestionsUiState(val query: String = "", val items: List<MentionAutocompleteItem> = emptyList()) {
    val isVisible: Boolean
        get() = items.isNotEmpty()
}

/**
 * Watches [editText] for an `@word` at the cursor and exposes the matching suggestions as
 * [state], to be rendered by [com.nextcloud.talk.ui.chat.MentionSuggestionList].
 *
 * Both text changes and cursor moves are observed: the latter through a [SpanWatcher] on the
 * selection spans, since [EditText] offers no selection listener.
 */
class MentionAutocompleteController(
    private val editText: EditText,
    scope: CoroutineScope,
    private val loadSuggestions: suspend (query: String) -> List<MentionAutocompleteItem>
) {
    private val activeQuery = MutableStateFlow<MentionQuery?>(null)
    private val _state = MutableStateFlow(MentionSuggestionsUiState())
    val state: StateFlow<MentionSuggestionsUiState> = _state.asStateFlow()

    val isShowing: Boolean
        get() = _state.value.isVisible

    private var block = false

    private val selectionWatcher = object : SpanWatcher {
        override fun onSpanAdded(text: Spannable, what: Any, start: Int, end: Int) = Unit

        override fun onSpanRemoved(text: Spannable, what: Any, start: Int, end: Int) = Unit

        override fun onSpanChanged(text: Spannable, what: Any, ostart: Int, oend: Int, nstart: Int, nend: Int) {
            if (what === Selection.SELECTION_END && ostart != nstart) {
                check(text)
            }
        }
    }

    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) = Unit

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) = Unit

        override fun afterTextChanged(s: Editable) {
            // setText() replaces the Editable and with it the span watcher
            attachSelectionWatcher(s)
            check(s)
        }
    }

    private val loadingJob: Job = scope.launch {
        activeQuery
            .map { it?.query }
            .distinctUntilChanged()
            .collectLatest { query ->
                if (query == null) {
                    _state.value = MentionSuggestionsUiState()
                } else {
                    _state.update { it.copy(query = query) }
                    val items = loadSuggestions(query)
                    _state.value = MentionSuggestionsUiState(query, items)
                }
            }
    }

    init {
        editText.addTextChangedListener(textWatcher)
        attachSelectionWatcher(editText.text)
    }

    /**
     * Replaces the `@word` at the cursor through [insert] and hides the suggestions.
     */
    fun select(item: MentionAutocompleteItem, insert: (Editable, MentionQuery, MentionAutocompleteItem) -> Unit) {
        val editable = editText.text ?: return
        val query = MentionQueryDetector.find(editable, Selection.getSelectionEnd(editable)) ?: return
        block = true
        try {
            insert(editable, query, item)
        } finally {
            block = false
        }
        dismiss()
    }

    fun dismiss() {
        activeQuery.value = null
    }

    fun detach() {
        editText.removeTextChangedListener(textWatcher)
        editText.text?.removeSpan(selectionWatcher)
        loadingJob.cancel()
        dismiss()
    }

    private fun attachSelectionWatcher(text: Spannable?) {
        if (text != null && text.getSpanStart(selectionWatcher) < 0) {
            text.setSpan(selectionWatcher, 0, text.length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
        }
    }

    private fun check(text: CharSequence) {
        if (block) {
            return
        }
        activeQuery.value = MentionQueryDetector.find(text, Selection.getSelectionEnd(text))
    }
}
