package com.nextcloud.talk.polls.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.PollCreateOptionsItemBinding

class PollCreateOptionsAdapter(
    private val clickListener: PollCreateOptionsItemClickListener
) : RecyclerView.Adapter<PollCreateOptionViewHolder>() {

    internal var list: MutableList<PollCreateOptionItem> = ArrayList<PollCreateOptionItem>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PollCreateOptionViewHolder {
        val itemBinding = PollCreateOptionsItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)

        return PollCreateOptionViewHolder(itemBinding)
    }

    override fun onBindViewHolder(holder: PollCreateOptionViewHolder, position: Int) {
        holder.bind(list[position], clickListener, position)
    }

    override fun getItemCount(): Int {
        return list.size
    }
}
