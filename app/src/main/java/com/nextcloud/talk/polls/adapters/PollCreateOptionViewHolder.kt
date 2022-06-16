package com.nextcloud.talk.polls.adapters

import android.annotation.SuppressLint
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.PollCreateOptionsItemBinding

class PollCreateOptionViewHolder(
    private val binding: PollCreateOptionsItemBinding
) : RecyclerView.ViewHolder(binding.root) {

    @SuppressLint("SetTextI18n")
    fun bind(
        pollCreateOptionItem: PollCreateOptionItem,
        clickListener: PollCreateOptionsItemClickListener,
        position: Int
    ) {
        // binding.root.setOnClickListener {  }

        binding.pollOptionDelete.setOnClickListener {
            clickListener.onDeleteClick(pollCreateOptionItem, position)
        }

        // binding.pollOptionText.addTextChangedListener(object : TextWatcher {
        //         override fun afterTextChanged(s: Editable) {
        //             // unused atm
        //         }
        //
        //         override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
        //             // unused atm
        //         }
        //
        //         override fun onTextChanged(option: CharSequence, start: Int, before: Int, count: Int) {
        //             pollCreateOptionItem.pollOption = option.toString()
        //         }
        //     })
    }

    // fun onBind(item: SharedItem) {
    //     Log.d("","bbbb")
    // }
    //
    // fun onLongClick(view: View?): Boolean {
    //     // moviesList.remove(getAdapterPosition())
    //     // notifyItemRemoved(getAdapterPosition())
    //
    //     Log.d("", "dfdrg")
    //     return true
    // }
    //
    // override fun onClick(v: View?) {
    //     Log.d("", "dfdrg")
    // }
}
