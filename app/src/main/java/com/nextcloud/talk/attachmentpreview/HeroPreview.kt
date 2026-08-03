/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.attachmentpreview

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.nextcloud.talk.R

private const val HERO_MAX_HEIGHT_DP = 480
private const val HERO_VERTICAL_SPACING_DP = 12
private const val HERO_ICON_SIZE_DP = 96
private const val HERO_OTHER_BORDER_WIDTH_DP = 1
private const val HERO_OTHER_BORDER_PADDING_DP = 24
private const val DETAIL_CHIP_CORNER_RADIUS_PERCENT = 50
private const val DETAIL_CHIP_TEXT_FADE_DURATION_MS = 200
private const val DETAIL_CHIP_PULSE_SCALE = 1.04f
private const val DETAIL_CHIP_PULSE_DURATION_MS = 150
private const val PLAY_BUTTON_SIZE_DP = 56
private const val PLAY_ICON_SIZE_DP = 32

/** The large, swipeable preview shown above the thumbnail strip. */
@Composable
internal fun HeroPreview(descriptions: List<FileDescription>, pagerState: PagerState, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = HERO_VERTICAL_SPACING_DP.dp),
        contentAlignment = Alignment.Center
    ) {
        HorizontalPager(
            state = pagerState,
            key = { page -> descriptions.getOrNull(page)?.uri ?: page },
            modifier = Modifier
                .fillMaxSize()
                .heightIn(max = HERO_MAX_HEIGHT_DP.dp)
        ) { page ->
            descriptions.getOrNull(page)?.let { description -> HeroPage(description) }
        }
    }
}

@Composable
private fun HeroPage(description: FileDescription) {
    var isPlayingVideo by remember(description.uri) { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        var cardModifier = description.aspectRatio?.let { ratio ->
            val (cardWidth, cardHeight) = fitWithinBounds(maxWidth, maxHeight, ratio)
            Modifier.size(cardWidth, cardHeight)
        } ?: Modifier.fillMaxSize()

        // Non-media files have no image content of their own to visually delimit the card, so
        // without an explicit outline the icon would appear to float directly on the dialog's
        // background instead of reading as a bounded preview, unlike images/videos.
        if (description.kind == MediaKind.OTHER) {
            cardModifier = cardModifier
                .padding(HERO_OTHER_BORDER_PADDING_DP.dp)
                .border(
                    width = HERO_OTHER_BORDER_WIDTH_DP.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(THUMBNAIL_CORNER_RADIUS_DP.dp)
                )
        }

        Box(modifier = cardModifier) {
            if (description.kind == MediaKind.VIDEO && isPlayingVideo) {
                VideoPlayerCard(uri = description.uri, modifier = Modifier.fillMaxSize())
            } else {
                FileThumbnailImage(
                    description,
                    iconSize = HERO_ICON_SIZE_DP.dp,
                    contentScale = ContentScale.Fit,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxSize()
                )

                if (description.kind == MediaKind.VIDEO) {
                    PlayButtonOverlay(
                        onClick = { isPlayingVideo = true },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                description.detail?.let { detail ->
                    HeroDetailOverlay(
                        detail,
                        description.alternateDetail,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayButtonOverlay(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(PLAY_BUTTON_SIZE_DP.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.5f))
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_baseline_play_arrow_voice_message_24),
            contentDescription = stringResource(R.string.media_message_content_play),
            tint = Color.White,
            modifier = Modifier.size(PLAY_ICON_SIZE_DP.dp)
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerCard(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) { ExoPlayer.Builder(context).build() }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // Preparing/starting playback here (instead of alongside player creation above) ensures the
    // PlayerView below has already been created and bound via `player = exoPlayer` first. Doing
    // both synchronously in the same step let the player start decoding before its SurfaceView
    // existed, silently dropping the first playback's frames — a black screen until the *next*
    // play attempt, once the surface had already been created once.
    LaunchedEffect(exoPlayer, uri) {
        exoPlayer.setMediaItem(MediaItem.fromUri(uri.toUri()))
        exoPlayer.playWhenReady = true
        exoPlayer.prepare()
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
            }
        },
        modifier = modifier
    )
}

private fun fitWithinBounds(maxWidth: Dp, maxHeight: Dp, ratio: Float): Pair<Dp, Dp> {
    val boundsRatio = maxWidth / maxHeight
    return if (boundsRatio > ratio) {
        (maxHeight * ratio) to maxHeight
    } else {
        maxWidth to (maxWidth / ratio)
    }
}

@Composable
private fun HeroDetailOverlay(detail: String, alternateDetail: String?, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    // The compressed-size estimate for `detail` is computed asynchronously (Dispatchers.IO), so it
    // lands a moment after the HQ button is tapped — keying off `detail` itself (rather than e.g. a
    // toggle counter bumped synchronously on tap) makes the pulse play exactly when the text is
    // about to change, instead of firing early and having the text/size catch up unanimated later.
    var hasComposedBefore by remember { mutableStateOf(false) }
    LaunchedEffect(detail) {
        if (hasComposedBefore) {
            // A small, smooth up-and-back pulse (no spring/overshoot) — just enough to acknowledge
            // the value changed without drawing much attention to itself.
            scale.animateTo(DETAIL_CHIP_PULSE_SCALE, animationSpec = tween(DETAIL_CHIP_PULSE_DURATION_MS))
            scale.animateTo(1f, animationSpec = tween(DETAIL_CHIP_PULSE_DURATION_MS))
        }
        hasComposedBefore = true
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(RoundedCornerShape(DETAIL_CHIP_CORNER_RADIUS_PERCENT))
            .background(Color.Black.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        // Invisible, always-laid-out copies of both possible detail texts. A Box without its own
        // fixed size sizes itself to its widest child, so keeping both variants present here (just
        // undrawn) permanently reserves width for whichever is wider — the chip then never resizes
        // when toggling HQ swaps which variant the AnimatedContent below actually shows.
        DetailText(detail, Modifier.alpha(0f))
        if (alternateDetail != null && alternateDetail != detail) {
            DetailText(alternateDetail, Modifier.alpha(0f))
        }

        // AnimatedContent doesn't animate its very first composition, so no extra guard is needed
        // here the way the pulse above needs `hasComposedBefore`.
        AnimatedContent(
            targetState = detail,
            transitionSpec = {
                fadeIn(tween(DETAIL_CHIP_TEXT_FADE_DURATION_MS))
                    .togetherWith(fadeOut(tween(DETAIL_CHIP_TEXT_FADE_DURATION_MS)))
            },
            label = "detailChipFade"
        ) { animatedDetail ->
            DetailText(animatedDetail)
        }
    }
}

@Composable
private fun DetailText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
