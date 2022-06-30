package com.nextcloud.talk.polls.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import com.nextcloud.talk.databinding.PollResultHeaderItemBinding

class PollResultHeaderViewHolder(
    override val binding: PollResultHeaderItemBinding
) : PollResultViewHolder(binding) {

    @SuppressLint("SetTextI18n")
    override fun bind(pollResultItem: PollResultItem, clickListener: PollResultItemClickListener) {
        val item = pollResultItem as PollResultHeaderItem

        binding.root.setOnClickListener { clickListener.onClick(pollResultItem) }

        binding.pollOptionText.text = item.name
        binding.pollOptionPercentText.text = "${item.percent}%"

        if (item.selfVoted) {
            binding.pollOptionText.setTypeface(null, Typeface.BOLD)
            binding.pollOptionPercentText.setTypeface(null, Typeface.BOLD)
        }

        binding.pollOptionBar.progress = item.percent
    }
}
