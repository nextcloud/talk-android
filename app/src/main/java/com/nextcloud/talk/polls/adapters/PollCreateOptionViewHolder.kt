/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.polls.adapters

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.PollCreateOptionsItemBinding
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import com.nextcloud.talk.utils.EmojiTextInputEditText

class PollCreateOptionViewHolder(
    private val binding: PollCreateOptionsItemBinding,
    private val viewThemeUtils: ViewThemeUtils
) : RecyclerView.ViewHolder(binding.root) {

    lateinit var optionText: EmojiTextInputEditText
    private var textListener: TextWatcher? = null

    @SuppressLint("SetTextI18n")
    fun bind(
        pollCreateOptionItem: PollCreateOptionItem,
        itemsListener: PollCreateOptionsItemListener,
        position: Int,
        focus: Boolean
    ) {
        textListener?.let {
            binding.pollOptionTextEdit.removeTextChangedListener(it)
        }

        binding.pollOptionTextEdit.setText(pollCreateOptionItem.pollOption)
        viewThemeUtils.material.colorTextInputLayout(binding.pollOptionTextInputLayout)

        if (focus) {
            itemsListener.requestFocus(binding.pollOptionTextEdit)
        }

        binding.pollOptionDelete.setOnClickListener {
            itemsListener.onRemoveOptionsItemClick(pollCreateOptionItem, position)
        }

        textListener = getTextWatcher(pollCreateOptionItem, itemsListener)
        binding.pollOptionTextEdit.addTextChangedListener(textListener)
        binding.pollOptionTextInputLayout.hint = String.format(
            binding.pollOptionTextInputLayout.resources.getString(R.string.polls_option_hint),
            position + 1
        )

        binding.pollOptionDelete.contentDescription = String.format(
            binding.pollOptionTextInputLayout.resources.getString(R.string.polls_option_delete),
            position + 1
        )
    }

    private fun getTextWatcher(
        pollCreateOptionItem: PollCreateOptionItem,
        itemsListener: PollCreateOptionsItemListener
    ) = object : TextWatcher {
        override fun afterTextChanged(s: Editable) {
            // unused atm
        }

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            // unused atm
        }

        override fun onTextChanged(option: CharSequence, start: Int, before: Int, count: Int) {
            pollCreateOptionItem.pollOption = option.toString()

            itemsListener.onOptionsItemTextChanged(pollCreateOptionItem)
        }
    }
}
