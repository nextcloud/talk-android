/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@email.com>
 * SPDX-FileCopyrightText: 2026 Jens Zalzala <anakin78z@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import android.content.Context
import androidx.compose.runtime.Composable
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.CircleCropTransformation
import coil.transform.RoundedCornersTransformation

@Composable
fun loadImage(imageUri: String?, context: Context, errorPlaceholderImage: Int): ImageRequest {
    val imageRequest = ImageRequest.Builder(context)
        .data(imageUri)
        .transformations(CircleCropTransformation())
        .error(errorPlaceholderImage)
        .placeholder(errorPlaceholderImage)
        .build()
    return imageRequest
}

@Composable
fun load(
    imageUri: String?,
    context: Context,
    errorPlaceholderImage: Int,
    animated: Boolean = false,
    authHeader: String? = null
): ImageRequest {
    val builder = ImageRequest.Builder(context)
        .data(imageUri)
        .size(Size.ORIGINAL)
        .error(errorPlaceholderImage)
        .placeholder(errorPlaceholderImage)
    if (!animated) {
        builder.transformations(RoundedCornersTransformation())
    }
    if (authHeader != null) {
        builder.addHeader("Authorization", authHeader)
    }
    return builder.build()
}
