package com.nextcloud.talk.adapters

import android.net.Uri
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.imagepipeline.common.RotationOptions
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.nextcloud.talk.databinding.AttachmentListItemBinding
import com.nextcloud.talk.repositories.SharedItem
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.FileViewerUtils

class SharedItemsListAdapter : RecyclerView.Adapter<SharedItemsListAdapter.ViewHolder>() {

    companion object {
        private val TAG = SharedItemsListAdapter::class.simpleName
        private const val ONE_SECOND_IN_MILLIS = 1000
    }

    class ViewHolder(val binding: AttachmentListItemBinding, itemView: View) : RecyclerView.ViewHolder(itemView)

    var authHeader: Map<String, String> = emptyMap()
    var items: List<SharedItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AttachmentListItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val currentItem = items[position]

        holder.binding.fileName.text = currentItem.name
        holder.binding.fileSize.text = Formatter.formatShortFileSize(
            holder.binding.fileSize.context,
            currentItem.fileSize.toLong()
        )
        holder.binding.fileDate.text = DateUtils.getLocalDateTimeStringFromTimestamp(
            currentItem.date * ONE_SECOND_IN_MILLIS
        )

        if (currentItem.previewAvailable) {
            val imageRequest = ImageRequestBuilder.newBuilderWithSource(Uri.parse(currentItem.previewLink))
                .setProgressiveRenderingEnabled(true)
                .setRotationOptions(RotationOptions.autoRotate())
                .disableDiskCache()
                .setHeaders(authHeader)
                .build()

            val draweeController: DraweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(holder.binding.fileImage.controller)
                .setAutoPlayAnimations(true)
                .setImageRequest(imageRequest)
                .build()
            holder.binding.fileImage.controller = draweeController
        } else {
            val drawableResourceId = DrawableUtils.getDrawableResourceIdForMimeType(currentItem.mimeType)
            val drawable = ContextCompat.getDrawable(holder.binding.fileImage.context, drawableResourceId)
            holder.binding.fileImage.hierarchy.setPlaceholderImage(drawable)
        }
        holder.binding.fileItem.setOnClickListener {
            val fileViewerUtils = FileViewerUtils(it.context, currentItem.userEntity)

            fileViewerUtils.openFile(
                FileViewerUtils.FileInfo(currentItem.id, currentItem.name, currentItem.fileSize),
                currentItem.path,
                currentItem.link,
                currentItem.mimeType,
                FileViewerUtils.ProgressUi(holder.binding.progressBar, null, holder.binding.fileImage)
            )
        }
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
