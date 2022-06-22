package com.nextcloud.talk.polls.adapters

import android.annotation.SuppressLint
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.PollResultItemBinding

class PollResultViewHolder(
    private val binding: PollResultItemBinding,
    private val showDetails: Boolean
) : RecyclerView.ViewHolder(binding.root) {

    @SuppressLint("SetTextI18n")
    fun bind(pollResultItem: PollResultItem, clickListener: PollResultItemClickListener) {
        binding.root.setOnClickListener { clickListener.onClick(pollResultItem) }
        binding.pollOptionText.text = pollResultItem.pollOption
        binding.pollOptionPercentText.text = pollResultItem.pollPercent.toString() + "%"
        binding.pollOptionBar.progress = pollResultItem.pollPercent

        if (showDetails) {
            binding.pollOptionDetail.visibility = View.VISIBLE
        } else {
            binding.pollOptionDetail.visibility = View.GONE
        }
    }
}
