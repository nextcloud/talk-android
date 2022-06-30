package com.nextcloud.talk.polls.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.PollResultHeaderItemBinding
import com.nextcloud.talk.databinding.PollResultVoterItemBinding
import com.nextcloud.talk.models.database.UserEntity

class PollResultsAdapter(
    private val user: UserEntity,
    private val clickListener: PollResultItemClickListener,
) : RecyclerView.Adapter<PollResultViewHolder>() {
    internal var list: MutableList<PollResultItem> = ArrayList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollResultViewHolder {
        when (viewType) {
            PollResultHeaderItem.VIEW_TYPE -> {
                val itemBinding = PollResultHeaderItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent,
                    false
                )
                return PollResultHeaderViewHolder(itemBinding)
            }
            PollResultVoterItem.VIEW_TYPE -> {
                val itemBinding = PollResultVoterItemBinding.inflate(
                    LayoutInflater.from(parent.context), parent,
                    false
                )
                return PollResultVoterViewHolder(user, itemBinding)
            }
        }

        val itemBinding = PollResultHeaderItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return PollResultHeaderViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: PollResultViewHolder, position: Int) {
        when (holder.itemViewType) {
            PollResultHeaderItem.VIEW_TYPE -> {
                val pollResultItem = list[position]
                holder.bind(pollResultItem as PollResultHeaderItem, clickListener)
            }
            PollResultVoterItem.VIEW_TYPE -> {
                val pollResultItem = list[position]
                holder.bind(pollResultItem as PollResultVoterItem, clickListener)
            }
        }
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun getItemViewType(position: Int): Int {
        return list[position].getViewType()
    }
}
