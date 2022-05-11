package com.nextcloud.talk.shareditems.adapters

import android.net.Uri
import android.text.format.Formatter
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.controller.ControllerListener
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.imagepipeline.common.RotationOptions
import com.facebook.imagepipeline.image.ImageInfo
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.nextcloud.talk.databinding.SharedItemListBinding
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.utils.DateUtils
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.FileViewerUtils
import com.nextcloud.talk.utils.FileViewerUtils.ProgressUi

class SharedItemsListAdapter : RecyclerView.Adapter<SharedItemsListAdapter.ViewHolder>() {

    companion object {
        private val TAG = SharedItemsListAdapter::class.simpleName
        private const val ONE_SECOND_IN_MILLIS = 1000
    }

    class ViewHolder(val binding: SharedItemListBinding, itemView: View) : RecyclerView.ViewHolder(itemView)

    var authHeader: Map<String, String> = emptyMap()
    var items: List<SharedItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = SharedItemListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val currentItem = items[position]

        holder.binding.fileName.text = currentItem.name
        holder.binding.fileSize.text = currentItem.fileSize?.let {
            Formatter.formatShortFileSize(
                holder.binding.fileSize.context,
                it
            )
        }
        holder.binding.fileDate.text = DateUtils.getLocalDateTimeStringFromTimestamp(
            currentItem.date * ONE_SECOND_IN_MILLIS
        )

        if (currentItem.previewAvailable == true) {
            val imageRequest = ImageRequestBuilder.newBuilderWithSource(Uri.parse(currentItem.previewLink))
                .setProgressiveRenderingEnabled(true)
                .setRotationOptions(RotationOptions.autoRotate())
                .disableDiskCache()
                .setHeaders(authHeader)
                .build()

            val listener: ControllerListener<ImageInfo?> = object : BaseControllerListener<ImageInfo?>() {
                override fun onFailure(id: String, e: Throwable) {
                    Log.w(TAG, "Failed to load image. A static mimetype image will be used", e)
                    setStaticMimetypeImage(currentItem, holder)
                }
            }

            val draweeController: DraweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(holder.binding.fileImage.controller)
                .setAutoPlayAnimations(true)
                .setImageRequest(imageRequest)
                .setControllerListener(listener)
                .build()
            holder.binding.fileImage.controller = draweeController
        } else {
            setStaticMimetypeImage(currentItem, holder)
        }

        val fileViewerUtils = FileViewerUtils(holder.binding.fileImage.context, currentItem.userEntity)

        holder.binding.fileItem.setOnClickListener {
            fileViewerUtils.openFile(
                FileViewerUtils.FileInfo(currentItem.id, currentItem.name, currentItem.fileSize),
                currentItem.path,
                currentItem.link,
                currentItem.mimeType,
                ProgressUi(
                    holder.binding.progressBar,
                    null,
                    holder.binding.fileImage
                )
            )
        }

        fileViewerUtils.resumeToUpdateViewsByProgress(
            currentItem.name,
            currentItem.id,
            currentItem.mimeType,
            ProgressUi(holder.binding.progressBar, null, holder.binding.fileImage)
        )
    }

    private fun setStaticMimetypeImage(
        currentItem: SharedItem,
        holder: ViewHolder
    ) {
        val drawableResourceId = DrawableUtils.getDrawableResourceIdForMimeType(currentItem.mimeType)
        val drawable = ContextCompat.getDrawable(holder.binding.fileImage.context, drawableResourceId)
        holder.binding.fileImage.hierarchy.setPlaceholderImage(drawable)
    }

    override fun getItemCount(): Int {
        return items.size
    }
}
