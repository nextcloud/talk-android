package com.nextcloud.talk.shareditems.adapters

import android.view.View
import android.widget.ProgressBar
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.databinding.SharedItemGridBinding
import com.nextcloud.talk.models.database.UserEntity

class SharedItemsGridViewHolder(
    override val binding: SharedItemGridBinding,
    userEntity: UserEntity
) : SharedItemsViewHolder(binding, userEntity) {

    override val image: SimpleDraweeView
        get() = binding.image
    override val clickTarget: View
        get() = binding.image
    override val progressBar: ProgressBar
        get() = binding.progressBar
}
