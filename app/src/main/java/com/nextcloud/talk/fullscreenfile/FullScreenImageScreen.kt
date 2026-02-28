/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2021 Dariusz Olszewski <starypatyk@gmail.com>
 * SPDX-FileCopyrightText: 2026 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.fullscreenfile

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.github.chrisbanes.photoview.PhotoView
import com.nextcloud.talk.R
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.utils.BitmapShrinker
import pl.droidsonroids.gif.GifDrawable
import pl.droidsonroids.gif.GifImageView

private const val TAG = "FullScreenImageScreen"
private const val MAX_SCALE = 6.0f
private const val MEDIUM_SCALE = 2.45f
private const val HUNDRED_MB = 100 * 1024 * 1024
private const val TOOLBAR_ALPHA = 0.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullScreenImageScreen(
    title: String,
    isGif: Boolean,
    imagePath: String,
    showFullscreen: Boolean,
    actions: FullScreenImageActions
) {
    val toolbarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        titleContentColor = Color.White,
        navigationIconContentColor = Color.White,
        actionIconContentColor = Color.White
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isGif) {
            GifView(imagePath = imagePath, onToggleFullscreen = actions.onToggleFullscreen)
        } else {
            PhotoImageView(
                imagePath = imagePath,
                onToggleFullscreen = actions.onToggleFullscreen,
                onBitmapError = actions.onBitmapError
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsBottomHeight(WindowInsets.navigationBars)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = TOOLBAR_ALPHA))
                    )
                )
                .align(Alignment.BottomCenter)
        )

        if (!showFullscreen) {
            val menuItems = buildList {
                add(stringResource(R.string.share) to actions.onShare)
                add(stringResource(R.string.nc_save_message) to actions.onSave)
            }
            Box {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = TOOLBAR_ALPHA), Color.Transparent)
                            )
                        )
                )
                StandardAppBar(title = title, menuItems = menuItems, colors = toolbarColors)
            }
        }
    }
}

@Composable
private fun GifView(imagePath: String, onToggleFullscreen: () -> Unit) {
    AndroidView(
        factory = { ctx ->
            GifImageView(ctx).apply {
                setImageDrawable(GifDrawable(imagePath))
                setOnClickListener { onToggleFullscreen() }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun PhotoImageView(imagePath: String, onToggleFullscreen: () -> Unit, onBitmapError: () -> Unit) {
    AndroidView(
        factory = { ctx ->
            PhotoView(ctx).apply {
                maximumScale = MAX_SCALE
                mediumScale = MEDIUM_SCALE
                setOnPhotoTapListener { _, _, _ -> onToggleFullscreen() }
                setOnOutsidePhotoTapListener { onToggleFullscreen() }
                val displayMetrics = ctx.resources.displayMetrics
                val bitmap = BitmapShrinker.shrinkBitmap(
                    imagePath,
                    displayMetrics.widthPixels * 2,
                    displayMetrics.heightPixels * 2
                )
                when {
                    bitmap == null -> {
                        Log.e(TAG, "bitmap could not be decoded from path: $imagePath")
                        onBitmapError()
                    }
                    bitmap.byteCount > HUNDRED_MB -> {
                        Log.e(TAG, "bitmap too large to display, skipping to avoid RuntimeException")
                        onBitmapError()
                    }
                    else -> setImageBitmap(bitmap)
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

data class FullScreenImageActions(
    val onShare: () -> Unit,
    val onSave: () -> Unit,
    val onToggleFullscreen: () -> Unit,
    val onBitmapError: () -> Unit
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun PreviewFullScreenImageLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        FullScreenImageScreen(
            title = "image.jpg",
            isGif = false,
            imagePath = "",
            showFullscreen = false,
            actions = FullScreenImageActions(onShare = {}, onSave = {}, onToggleFullscreen = {}, onBitmapError = {})
        )
    }
}

@Preview(name = "Dark - RTL Arabic", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "ar")
@Composable
private fun PreviewFullScreenImageDarkRtl() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        FullScreenImageScreen(
            title = "صورة.jpg",
            isGif = false,
            imagePath = "",
            showFullscreen = false,
            actions = FullScreenImageActions(onShare = {}, onSave = {}, onToggleFullscreen = {}, onBitmapError = {})
        )
    }
}
