/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-FileCopyrightText: 2023 Parneet Singh <gurayaparneet@gmail.com>
 * SPDX-FileCopyrightText: 2023 Ezhil Shanmugham <ezhil56x.contact@gmail.com>
 * SPDX-FileCopyrightText: 2026 Enrique López-Mañas <eenriquelopez@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.fullscreenfile

import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import com.nextcloud.talk.R
import com.nextcloud.talk.components.StandardAppBar

private const val TOOLBAR_ALPHA = 0.5f

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FullScreenMediaScreen(title: String, player: ExoPlayer?, actions: FullScreenMediaActions) {
    val toolbarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        titleContentColor = Color.White,
        navigationIconContentColor = Color.White,
        actionIconContentColor = Color.White
    )

    var showToolbar by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        MediaPlayerView(
            player = player,
            onControllerVisible = {
                showToolbar = true
                actions.onExitImmersive()
            },
            onControllerHidden = {
                showToolbar = false
                actions.onEnterImmersive()
            }
        )

        BottomGradient(modifier = Modifier.align(Alignment.BottomCenter))

        if (showToolbar) {
            ToolbarOverlay(title = title, toolbarColors = toolbarColors, actions = actions)
        }
    }
}

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MediaPlayerView(player: ExoPlayer?, onControllerVisible: () -> Unit, onControllerHidden: () -> Unit) {
    if (LocalInspectionMode.current) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    // Deliberately "IgnoringVisibility": the plain systemBars value animates across many frames
    // while we hide/show the bars (see onEnterImmersive/onExitImmersive), and reapplying these
    // margins on every one of those frames fights the ExoPlayer controller's own hide animation,
    // flipping it back to visible mid-fade and looping forever.
    val bottomPx = WindowInsets.systemBarsIgnoringVisibility.getBottom(density)
    val leftPx = WindowInsets.systemBarsIgnoringVisibility.getLeft(density, layoutDirection)
    val rightPx = WindowInsets.systemBarsIgnoringVisibility.getRight(density, layoutDirection)
    val originalProgressMarginBottom = remember { intArrayOf(-1) }
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }

    LaunchedEffect(player) {
        playerViewRef.value?.apply {
            this.player = player
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                // Audio only (this screen is no longer used for video - see FileViewerUtils):
                // keep the controls up indefinitely rather than auto-hiding them.
                controllerShowTimeoutMs = 0
                showController()
                setControllerVisibilityListener(
                    PlayerView.ControllerVisibilityListener { visibility ->
                        if (visibility == View.VISIBLE) onControllerVisible() else onControllerHidden()
                    }
                )
                playerViewRef.value = this
            }
        },
        update = { playerView ->
            val exoControls = playerView.findViewById<FrameLayout>(R.id.exo_bottom_bar)
            val exoProgress = playerView.findViewById<DefaultTimeBar>(R.id.exo_progress)
            exoControls?.apply {
                updateLayoutParams<MarginLayoutParams> { bottomMargin = bottomPx }
                updatePadding(left = leftPx, right = rightPx)
            }
            exoProgress?.apply {
                if (originalProgressMarginBottom[0] < 0) {
                    originalProgressMarginBottom[0] =
                        (layoutParams as? MarginLayoutParams)?.bottomMargin ?: 0
                }
                updateLayoutParams<MarginLayoutParams> {
                    bottomMargin = bottomPx + originalProgressMarginBottom[0]
                }
                updatePadding(left = leftPx, right = rightPx)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun BottomGradient(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = TOOLBAR_ALPHA))
                )
            )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolbarOverlay(title: String, toolbarColors: TopAppBarColors, actions: FullScreenMediaActions) {
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

data class FullScreenMediaActions(
    val onShare: () -> Unit,
    val onSave: () -> Unit,
    val onEnterImmersive: () -> Unit,
    val onExitImmersive: () -> Unit
)

@Preview(name = "Light", showBackground = true)
@Composable
private fun PreviewFullScreenMediaLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        FullScreenMediaScreen(
            title = "audio.mp3",
            player = null,
            actions = FullScreenMediaActions(onShare = {}, onSave = {}, onEnterImmersive = {}, onExitImmersive = {})
        )
    }
}

@Preview(name = "Dark - RTL Arabic", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, locale = "ar")
@Composable
private fun PreviewFullScreenMediaDarkRtlArabic() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        FullScreenMediaScreen(
            title = "صوت.mp3",
            player = null,
            actions = FullScreenMediaActions(onShare = {}, onSave = {}, onEnterImmersive = {}, onExitImmersive = {})
        )
    }
}
