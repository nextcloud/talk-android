/*
 * Nextcloud Talk application
 *
 * @author Tim Krüger
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Tim Krüger <t@timkrueger.me>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.shareditems.adapters

import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.drawee.controller.BaseControllerListener
import com.facebook.drawee.controller.ControllerListener
import com.facebook.drawee.interfaces.DraweeController
import com.facebook.drawee.view.SimpleDraweeView
import com.facebook.imagepipeline.common.RotationOptions
import com.facebook.imagepipeline.image.ImageInfo
import com.facebook.imagepipeline.request.ImageRequestBuilder
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.shareditems.model.SharedItem
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DrawableUtils
import com.nextcloud.talk.utils.FileViewerUtils

abstract class SharedItemsViewHolder(
    open val binding: ViewBinding,
    private val userEntity: UserEntity
) : RecyclerView.ViewHolder(binding.root) {

    companion object {
        private val TAG = SharedItemsViewHolder::class.simpleName
    }

    abstract val image: SimpleDraweeView
    abstract val clickTarget: View
    abstract val progressBar: ProgressBar

    private val authHeader = mapOf(
        Pair(
            "Authorization",
            ApiUtils.getCredentials(userEntity.username, userEntity.token)
        )
    )

    open fun onBind(item: SharedItem) {
        image.hierarchy.setPlaceholderImage(staticImage(item.mimeType, image))
        if (item.previewAvailable == true) {
            image.controller = configurePreview(item)
        }

        /*
        The FileViewerUtils forces us to do things at this points which should be done separated in the activity and
        the view model.

        This should be done after a refactoring of FileViewerUtils.
         */
        val fileViewerUtils = FileViewerUtils(image.context, userEntity)

        clickTarget.setOnClickListener {
            fileViewerUtils.openFile(
                FileViewerUtils.FileInfo(item.id, item.name, item.fileSize),
                item.path,
                item.link,
                item.mimeType,
                FileViewerUtils.ProgressUi(
                    progressBar,
                    null,
                    image
                )
            )
        }

        fileViewerUtils.resumeToUpdateViewsByProgress(
            item.name,
            item.id,
            item.mimeType,
            FileViewerUtils.ProgressUi(progressBar, null, image)
        )
    }

    private fun configurePreview(item: SharedItem): DraweeController {

        val imageRequest = ImageRequestBuilder.newBuilderWithSource(Uri.parse(item.previewLink))
            .setProgressiveRenderingEnabled(true)
            .setRotationOptions(RotationOptions.autoRotate())
            .disableDiskCache()
            .setHeaders(authHeader)
            .build()

        val listener: ControllerListener<ImageInfo?> = object : BaseControllerListener<ImageInfo?>() {
            override fun onFailure(id: String, e: Throwable) {
                Log.w(TAG, "Failed to load image. A static mimetype image will be used", e)
            }
        }

        return Fresco.newDraweeControllerBuilder()
            .setOldController(image.controller)
            .setAutoPlayAnimations(true)
            .setImageRequest(imageRequest)
            .setControllerListener(listener)
            .build()
    }

    private fun staticImage(
        mimeType: String?,
        image: SimpleDraweeView
    ): Drawable {
        val drawableResourceId = DrawableUtils.getDrawableResourceIdForMimeType(mimeType)
        return ContextCompat.getDrawable(image.context, drawableResourceId)!!
    }
}
