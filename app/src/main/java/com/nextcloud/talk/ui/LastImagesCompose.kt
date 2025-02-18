/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.os.bundleOf
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication

@AutoInjector(NextcloudTalkApplication::class)
class LastImagesCompose {

    companion object {
        private const val LIMIT = 10
        private const val IMAGE_HEIGHT = 50
        private const val IMAGE_WIDTH = 50
    }

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    @Composable
    fun GetView(context: Context) {
        val thumbnails = getLast10Images(context)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {

            items(thumbnails) { bitmap ->
                LastImageItem(bitmap)
            }

            item {
                ShowGalleryButton()
            }

        }
    }

    // FIXME check if this works on my physical device
    private fun getLast10Images(context: Context): List<Bitmap> {
        val thumbnails = mutableListOf<Bitmap>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
        )

        val queryArgs = bundleOf()
        queryArgs.putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        queryArgs.putInt(ContentResolver.QUERY_ARG_LIMIT, LIMIT)

        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            queryArgs,
            null
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.loadThumbnail(
                        contentUri, Size(IMAGE_WIDTH, IMAGE_HEIGHT), null)
                } else {
                    val kind = MediaStore.Images.Thumbnails.MINI_KIND
                    MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, kind, null)
                }

                thumbnails.add(bitmap)
            }
        }

        Log.d("Julius", "Size: ${thumbnails.size}")
        return thumbnails
    }

    @Composable
    private fun LastImageItem(bitmap: Bitmap) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            modifier = Modifier.size(IMAGE_HEIGHT.dp),
            contentDescription = "Image from Gallery" // TODO make this more accessible
        )
    }

    @Composable
    private fun ShowGalleryButton() {
        val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) {
                Log.d("Julius", "Selected URI: $uri") // TODO add to chat
            } else {
                Log.d("Julius", "No media selected")
            }
        }
        IconButton(onClick = {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }) {
            Icon(
                imageVector = Icons.Filled.AddCircle,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }

    }
}
