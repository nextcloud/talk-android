package com.nextcloud.talk.polls.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.PollResultItemBinding

class PollResultsAdapter(
    private val clickListener: PollResultItemClickListener,
    private val showDetails: Boolean
) : RecyclerView.Adapter<PollResultViewHolder>() {
    internal var list: MutableList<PollResultItem> = ArrayList<PollResultItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollResultViewHolder {
        val itemBinding = PollResultItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PollResultViewHolder(itemBinding, showDetails)
    }

    override fun onBindViewHolder(holder: PollResultViewHolder, position: Int) {
        holder.bind(list[position], clickListener)
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
