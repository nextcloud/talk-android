/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.animation.ValueAnimator
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.ui.theme.ViewThemeUtils

@Suppress("LongParameterList")
class BackgroundVoiceMessageCard(
    val name: String,
    val duration: Int,
    private val offset: Float,
    private val imageURI: String,
    private val conversationImageURI: String,
    private var viewThemeUtils: ViewThemeUtils,
    private var context: Context
) {

    private val progressState = mutableFloatStateOf(0.0f)
    private val animator = ValueAnimator.ofFloat(offset, 1.0f)

    init {
        animator.duration = duration.toLong()
        animator.addUpdateListener { animation ->
            progressState.floatValue = animation.animatedValue as Float
        }

        animator.start()
    }

    companion object {
        private const val ACCOUNT_WEIGHT = .8f
    }

    @Suppress("FunctionNaming", "LongMethod")
    @Composable
    fun GetView(onPlayPaused: (isPaused: Boolean) -> Unit, onClosed: () -> Unit) {
        MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(context)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .padding(16.dp, 0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                        .fillMaxWidth(progressState.floatValue)
                        .height(4.dp)
                )
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    ) {
                        var isPausedIcon by remember { mutableStateOf(false) }

                        IconButton(
                            onClick = {
                                isPausedIcon = !isPausedIcon
                                onPlayPaused(isPausedIcon)
                                if (isPausedIcon) {
                                    animator.pause()
                                } else {
                                    animator.resume()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (isPausedIcon) {
                                    Icons.Filled.PlayArrow
                                } else {
                                    ImageVector.vectorResource(R.drawable.ic_baseline_pause_voice_message_24)
                                },
                                contentDescription = "contentDescription",
                                modifier = Modifier
                                    .size(24.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(16.dp))

                    Box(
                        modifier = Modifier
                            .weight(ACCOUNT_WEIGHT)
                            .align(Alignment.CenterVertically),
                        contentAlignment = Alignment.Center
                    ) {
                        Row {
                            Box {
                                val errorPlaceholderImage: Int = R.drawable.account_circle_96dp
                                val loadedImage = loadImage(imageURI, context, errorPlaceholderImage)
                                val conversationImage = loadImage(
                                    conversationImageURI,
                                    context,
                                    errorPlaceholderImage
                                )
                                AsyncImage(
                                    model = conversationImage,
                                    contentDescription = stringResource(R.string.user_avatar),
                                    modifier = Modifier
                                        .size(width = 45.dp, height = 45.dp)
                                        .padding(8.dp)
                                        .offset(10.dp, 10.dp)
                                )

                                AsyncImage(
                                    model = loadedImage,
                                    contentDescription = stringResource(R.string.user_avatar),
                                    modifier = Modifier
                                        .size(width = 45.dp, height = 45.dp)
                                        .padding(8.dp)
                                )
                            }

                            Text(
                                name,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically)
                                    .padding(8.dp),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    ) {
                        IconButton(
                            onClick = {
                                onClosed()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "contentDescription",
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(2.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
