/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.polls.adapters

import android.annotation.SuppressLint
import android.text.Editable
import android.text.TextWatcher
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.PollCreateOptionsItemBinding
import com.nextcloud.talk.utils.EmojiTextInputEditText

class PollCreateOptionViewHolder(
    private val binding: PollCreateOptionsItemBinding
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
            binding.pollOptionText.removeTextChangedListener(it)
        }

        binding.pollOptionText.setText(pollCreateOptionItem.pollOption)

        if (focus) {
            itemsListener.requestFocus(binding.pollOptionText)
        }

        binding.pollOptionDelete.setOnClickListener {
            itemsListener.onRemoveOptionsItemClick(pollCreateOptionItem, position)
        }

        textListener = getTextWatcher(pollCreateOptionItem, itemsListener)
        binding.pollOptionText.addTextChangedListener(textListener)
    }

    private fun getTextWatcher(
        pollCreateOptionItem: PollCreateOptionItem,
        itemsListener: PollCreateOptionsItemListener
    ) =
        object : TextWatcher {
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
