package com.nextcloud.talk.shareditems.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.nextcloud.talk.databinding.SharedItemGridBinding
import com.nextcloud.talk.databinding.SharedItemListBinding
import com.nextcloud.talk.shareditems.model.SharedItem

class SharedItemsAdapter(
    private val showGrid: Boolean,
    private val userName: String,
    private val userToken: String
) : RecyclerView.Adapter<SharedItemsViewHolder>() {

    var items: List<SharedItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SharedItemsViewHolder {

        return if (showGrid) {
            SharedItemsGridViewHolder(
                SharedItemGridBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                userName,
                userToken
            )
        } else {
            SharedItemsListViewHolder(
                SharedItemListBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                ),
                userName,
                userToken
            )
        }
    }

    override fun onBindViewHolder(holder: SharedItemsViewHolder, position: Int) {
        holder.onBind(items[position])
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
