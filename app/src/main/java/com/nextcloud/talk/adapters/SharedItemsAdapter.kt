package com.nextcloud.talk.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.imagepipeline.common.RotationOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.nextcloud.talk.databinding.AttachmentItemBinding
import com.nextcloud.talk.repositories.SharedItem

class SharedItemsAdapter : RecyclerView.Adapter<SharedItemsAdapter.ViewHolder>() {

    class ViewHolder(val binding: AttachmentItemBinding, itemView: View) : RecyclerView.ViewHolder(itemView)

    var authHeader: Map<String, String> = emptyMap()
    var items: List<SharedItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AttachmentItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val currentItem = items[position]

        if (currentItem.previewAvailable) {
            val imageRequest = ImageRequestBuilder.newBuilderWithSource(Uri.parse(currentItem.previewLink))
                .setProgressiveRenderingEnabled(true)
                .setRotationOptions(RotationOptions.autoRotate())
                .disableDiskCache()
                .setHeaders(authHeader)
                .build()

            val draweeController: DraweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(holder.binding.image.controller)
                .setAutoPlayAnimations(true)
                .setImageRequest(imageRequest)
                .build()
            holder.binding.image.controller = draweeController
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
