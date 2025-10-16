/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils.message

import android.content.Context
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.LinearLayout
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.emoji2.widget.EmojiTextView
import com.nextcloud.android.common.ui.theme.utils.ColorRole
import com.nextcloud.talk.chat.data.model.ChatMessage
import com.nextcloud.talk.ui.theme.ViewThemeUtils

object MessageCheckboxUtils {

    private const val LINE_SPACING_MULTIPLIER = 1.2f
    private const val LINE_SPACING_ADDER = 0f
    private const val TASK_TEXT_GROUP_INDEX = 2
    val CHECKBOX_REGEX = """- \[(X|x| )]\s*(.+)""".toRegex()

    fun matchCheckbox(line: String) = CHECKBOX_REGEX.matchEntire(line.trim())

    @Suppress("LongParameterList")
    fun addCheckboxLine(
        context: Context,
        container: ViewGroup,
        chatMessage: ChatMessage,
        taskText: String,
        isChecked: Boolean,
        isEnabled: Boolean,
        isIncomingMessage: Boolean,
        messageUtils: MessageUtils,
        viewThemeUtils: ViewThemeUtils,
        @ColorRes linkColorRes: Int,
        paddingPx: Int,
        onCheckedChange: (CompoundButton, Boolean) -> Unit
    ): CheckBox {
        val checkBoxLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val checkBox = CheckBox(context).apply {
            this.isChecked = isChecked
            this.isEnabled = isEnabled
            setOnCheckedChangeListener(onCheckedChange)
            setLineSpacing(LINE_SPACING_ADDER, LINE_SPACING_MULTIPLIER)
        }
        val textView = EmojiTextView(context).apply {
            val messageText = messageUtils.enrichChatMessageText(
                context,
                taskText,
                isIncomingMessage,
                viewThemeUtils
            )
            text = messageUtils.processMessageParameters(
                context,
                viewThemeUtils,
                messageText,
                chatMessage,
                null
            )
            setTextColor(ContextCompat.getColor(context, linkColorRes))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            movementMethod = LinkMovementMethod.getInstance()
            setLineSpacing(LINE_SPACING_ADDER, LINE_SPACING_MULTIPLIER)
        }
        checkBoxLayout.addView(checkBox)
        checkBoxLayout.addView(textView)
        container.addView(checkBoxLayout)
        Linkify.addLinks(textView, Linkify.ALL)
        textView.setLinkTextColor(ContextCompat.getColor(context, linkColorRes))
        setPaddingForView(chatMessage, checkBox, paddingPx)
        viewThemeUtils.platform.themeCheckbox(checkBox)
        return checkBox
    }

    @Suppress("LongParameterList")
    fun addPlainTextLine(
        context: Context,
        container: ViewGroup,
        chatMessage: ChatMessage,
        text: String,
        isIncomingMessage: Boolean,
        messageUtils: MessageUtils,
        viewThemeUtils: ViewThemeUtils,
        @ColorRes linkColorRes: Int,
        paddingPx: Int
    ) {
        val textView = EmojiTextView(context).apply {
            val messageText = messageUtils.enrichChatMessageText(context, text, isIncomingMessage, viewThemeUtils)
            this.text = messageUtils.processMessageParameters(
                context,
                viewThemeUtils,
                messageText,
                chatMessage,
                null
            )
            viewThemeUtils.platform.colorTextView(this, ColorRole.ON_SURFACE_VARIANT)
            isClickable = true
            movementMethod = LinkMovementMethod.getInstance()
            setLineSpacing(LINE_SPACING_ADDER, LINE_SPACING_MULTIPLIER)
        }
        setPaddingForView(chatMessage, textView, paddingPx)
        container.addView(textView)
        Linkify.addLinks(textView, Linkify.ALL)
        textView.setLinkTextColor(ContextCompat.getColor(context, linkColorRes))
    }

    fun updateMessageWithCheckboxStates(originalMessage: String, checkboxes: List<CheckBox>): String {
        var checkboxIndex = 0
        return originalMessage.lines().joinToString("\n") { line ->
            val match = CHECKBOX_REGEX.matchEntire(line.trim())
            if (match != null) {
                val taskText = match.groupValues[TASK_TEXT_GROUP_INDEX].trim()
                val state = if (checkboxes.getOrNull(checkboxIndex)?.isChecked == true) "X" else " "
                checkboxIndex++
                "- [$state] $taskText"
            } else {
                line
            }
        }
    }

    private fun setPaddingForView(chatMessage: ChatMessage, view: View, paddingInPx: Int) {
        if (chatMessage.messageParameters != null) {
            view.setPadding(0, paddingInPx, 0, paddingInPx)
        }
    }
}
