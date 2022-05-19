package com.nextcloud.talk.shareditems.adapters

import android.text.format.Formatter
import android.view.View
import android.widget.ProgressBar
import com.facebook.drawee.view.SimpleDraweeView
import com.nextcloud.talk.databinding.SharedItemListBinding
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.utils.DateUtils

class SharedItemsListViewHolder(
    override val binding: SharedItemListBinding,
    userName: String,
    userToken: String
) : SharedItemsViewHolder(binding, userName, userToken) {

    override val image: SimpleDraweeView
        get() = binding.fileImage
    override val clickTarget: View
        get() = binding.fileItem
    override val progressBar: ProgressBar
        get() = binding.progressBar

    override fun onBind(item: SharedItem) {

        super.onBind(item)

        binding.fileName.text = item.name
        binding.fileSize.text = item.fileSize?.let {
            Formatter.formatShortFileSize(
                binding.fileSize.context,
                it
            )
        }
        binding.fileDate.text = DateUtils.getLocalDateTimeStringFromTimestamp(
            item.date * ONE_SECOND_IN_MILLIS
        )
    }

    companion object {
        private const val ONE_SECOND_IN_MILLIS = 1000
    }
}
