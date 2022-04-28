package com.nextcloud.talk.adapters

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.RotationOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.nextcloud.talk.R
import com.nextcloud.talk.databinding.AttachmentItemBinding
import com.nextcloud.talk.repositories.SharedItem
import com.nextcloud.talk.utils.FileViewerUtils

class SharedItemsAdapter : RecyclerView.Adapter<SharedItemsAdapter.ViewHolder>() {

    companion object {
        private val TAG = SharedItemsAdapter::class.simpleName
    }

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
        } else {
            when (currentItem.mimeType) {
                "video/mp4",
                "video/quicktime",
                "video/ogg"
                -> holder.binding.image.setImageResource(R.drawable.ic_mimetype_video)
                "audio/mpeg",
                "audio/wav",
                "audio/ogg",
                -> holder.binding.image.setImageResource(R.drawable.ic_mimetype_audio)
                "image/png",
                "image/jpeg",
                "image/gif"
                -> holder.binding.image.setImageResource(R.drawable.ic_mimetype_image)
                "text/markdown",
                "text/plain"
                -> holder.binding.image.setImageResource(R.drawable.ic_mimetype_text)
                else
                -> holder.binding.image.setImageResource(R.drawable.ic_mimetype_file)
            }
        }
        holder.binding.image.setOnClickListener {
            val fileViewerUtils = FileViewerUtils(it.context, currentItem.userEntity)

            fileViewerUtils.openFile(
                FileViewerUtils.FileInfo(currentItem.id, currentItem.name, currentItem.fileSize),
                currentItem.path,
                currentItem.link,
                currentItem.mimeType,
                FileViewerUtils.ProgressUi(
                    holder.binding.progressBar,
                    null,
                    it as SimpleDraweeView
                )
            )
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
