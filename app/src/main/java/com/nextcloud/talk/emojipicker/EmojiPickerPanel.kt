/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.emojipicker

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import com.nextcloud.talk.databinding.EmojiPickerPanelBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Self-contained emoji picker: the bundled androidx EmojiPickerView grid, plus an optional
 * keyword search field (backed by [EmojiKeywordProvider]) and an optional backspace button,
 * so every screen that embeds an emoji picker can opt into the same search/backspace behavior
 * instead of reimplementing it. [searchEnabled] and [backspaceEnabled] control which of those
 * are shown, and [onEmojiPicked]/[onBackspaceClicked] are how the host reacts to them - this
 * view has no knowledge of where the emoji or the backspace action actually get applied.
 */
class EmojiPickerPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val binding = EmojiPickerPanelBinding.inflate(LayoutInflater.from(context), this)
    private val emojiKeywordProvider by lazy { EmojiKeywordProvider(context) }
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var searchJob: Job? = null
    private var recentEmojiProvider: RecentEmojiProvider? = null
    private var searchFieldFocused = false

    var searchEnabled: Boolean = true
        set(value) {
            field = value
            binding.emojiSearchFieldGroup.isVisible = value
            updateSearchRowVisibility()
        }

    var backspaceEnabled: Boolean = false
        set(value) {
            field = value
            updateBackspaceVisibility()
            updateSearchRowVisibility()
        }

    /** Whether the host's target text actually has something for the backspace button to delete. */
    var backspaceActionAvailable: Boolean = true
        set(value) {
            field = value
            binding.emojiBackspaceButton.isEnabled = value
            binding.emojiBackspaceButton.alpha = if (value) ENABLED_ALPHA else DISABLED_ALPHA
        }

    var onEmojiPicked: ((String) -> Unit)? = null
    var onBackspaceClicked: (() -> Unit)? = null

    val emojiPickerView: EmojiPickerView get() = binding.emojiPicker

    init {
        orientation = VERTICAL
        binding.emojiSearchFieldGroup.isVisible = searchEnabled
        updateBackspaceVisibility()
        updateSearchRowVisibility()
        binding.emojiBackspaceButton.alpha = ENABLED_ALPHA

        binding.emojiPicker.setOnEmojiPickedListener { item -> onEmojiPicked?.invoke(item.emoji) }
        binding.emojiSearchInput.doAfterTextChanged { editable -> onSearchTextChanged(editable?.toString().orEmpty()) }
        binding.emojiSearchInput.setOnFocusChangeListener { _, hasFocus ->
            searchFieldFocused = hasFocus
            updateBackspaceVisibility()
        }
        binding.emojiSearchClear.setOnClickListener { binding.emojiSearchInput.setText("") }
        binding.emojiBackspaceButton.setOnClickListener { onBackspaceClicked?.invoke() }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        coroutineScope.cancel()
    }

    fun setRecentEmojiProvider(provider: RecentEmojiProvider) {
        recentEmojiProvider = provider
        binding.emojiPicker.setRecentEmojiProvider(provider)
    }

    /** Clears any search query and returns to showing the emoji grid. */
    fun reset() {
        searchJob?.cancel()
        binding.emojiSearchInput.setText("")
        binding.emojiSearchInput.clearFocus()
        showGrid()
    }

    /**
     * Applies the host's dynamic per-server branding to every themeable part of the panel:
     * the picker's background and selected-category tab (see [themeEmojiPickerCategoryTabs]),
     * its scroll-gesture fix (see [protectEmojiPickerScrollGesture]), and the search field's
     * hint/text/icon colors, which otherwise fall back to the static app theme instead of
     * following that branding.
     */
    fun applyTheme(
        backgroundColor: Int,
        selectedTabColor: Int,
        unselectedTabColor: Int,
        hintTextColor: Int,
        textColor: Int
    ) {
        setBackgroundColor(backgroundColor)
        binding.emojiSearchInput.setHintTextColor(hintTextColor)
        binding.emojiSearchInput.setTextColor(textColor)
        val iconTint = ColorStateList.valueOf(hintTextColor)
        binding.emojiSearchIcon.imageTintList = iconTint
        binding.emojiSearchClear.imageTintList = iconTint
        binding.emojiBackspaceButton.imageTintList = iconTint
        themeEmojiPickerCategoryTabs(emojiPickerView, selectedTabColor, unselectedTabColor)
        protectEmojiPickerScrollGesture(emojiPickerView)
    }

    private fun updateSearchRowVisibility() {
        binding.emojiSearchRow.isVisible = searchEnabled || backspaceEnabled
    }

    /**
     * Hidden while the search field has focus - with the keyboard open for typing a search
     * term, a backspace button right next to it reads as belonging to the search field rather
     * than to whatever text field the host's own [onBackspaceClicked] actually edits.
     */
    private fun updateBackspaceVisibility() {
        binding.emojiBackspaceButton.isVisible = backspaceEnabled && !searchFieldFocused
    }

    private fun onSearchTextChanged(query: String) {
        binding.emojiSearchClear.isVisible = query.isNotEmpty()
        searchJob?.cancel()

        if (query.isBlank()) {
            showGrid()
            return
        }

        searchJob = coroutineScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val results = emojiKeywordProvider.search(query)
            binding.emojiPicker.isVisible = false
            populateSearchResults(results)
        }
    }

    private fun showGrid() {
        binding.emojiPicker.isVisible = true
        binding.emojiSearchResultsScroll.isVisible = false
        binding.emojiSearchNoResults.isVisible = false
    }

    private fun populateSearchResults(results: List<String>) {
        binding.emojiSearchResultsFlexbox.removeAllViews()

        if (results.isEmpty()) {
            binding.emojiSearchResultsScroll.isVisible = false
            binding.emojiSearchNoResults.isVisible = true
            return
        }

        binding.emojiSearchNoResults.isVisible = false
        binding.emojiSearchResultsScroll.isVisible = true

        val rippleBackground = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, rippleBackground, true)
        val paddingPx = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            RESULT_PADDING_DP.toFloat(),
            resources.displayMetrics
        ).toInt()

        results.forEach { emoji ->
            val emojiView = TextView(context).apply {
                text = emoji
                textSize = RESULT_TEXT_SIZE_SP
                setPadding(paddingPx, paddingPx, paddingPx, paddingPx)
                setBackgroundResource(rippleBackground.resourceId)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    recentEmojiProvider?.recordSelection(emoji)
                    onEmojiPicked?.invoke(emoji)
                }
            }
            binding.emojiSearchResultsFlexbox.addView(emojiView)
        }
    }

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 150L
        private const val RESULT_TEXT_SIZE_SP = 28f
        private const val RESULT_PADDING_DP = 8
        private const val ENABLED_ALPHA = 1f
        private const val DISABLED_ALPHA = 0.38f
    }
}
