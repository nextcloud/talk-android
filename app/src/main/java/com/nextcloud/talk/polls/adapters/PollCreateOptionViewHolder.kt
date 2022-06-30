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
        position: Int
    ) {

        textListener?.let {
            binding.pollOptionText.removeTextChangedListener(it)
        }

        binding.pollOptionText.setText(pollCreateOptionItem.pollOption)

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
