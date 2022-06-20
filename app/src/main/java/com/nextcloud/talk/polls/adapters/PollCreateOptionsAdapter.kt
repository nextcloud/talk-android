package com.nextcloud.talk.polls.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.PollCreateOptionsItemBinding

class PollCreateOptionsAdapter(
    private val clickListener: PollCreateOptionsItemListener
) : RecyclerView.Adapter<PollCreateOptionViewHolder>() {

    internal var list: ArrayList<PollCreateOptionItem> = ArrayList<PollCreateOptionItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollCreateOptionViewHolder {
        val itemBinding = PollCreateOptionsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return PollCreateOptionViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: PollCreateOptionViewHolder, position: Int) {
        val currentItem = list[position]
        holder.bind(currentItem, clickListener, position)
    }

    override fun getItemCount(): Int {
        return list.size
    }

    fun updateOptionsList(optionsList: ArrayList<PollCreateOptionItem>) {
        list = optionsList
        notifyDataSetChanged()
    }
}
