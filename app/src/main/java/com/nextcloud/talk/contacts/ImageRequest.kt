/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts

import android.content.Context
import androidx.compose.runtime.Composable
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation

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
