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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class BackgroundVoiceMessageSeekbarCard(val name: String, val duration: Int, val offset: Float) {

    // TODO get avatar

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var context: Context

    private val progressState = mutableFloatStateOf(0.01f)
    private val animator = ValueAnimator.ofFloat(offset, 1.0f)

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
        animator.duration = duration.toLong()
        animator.addUpdateListener { animation ->
            progressState.floatValue = animation.animatedValue as Float
        }

        animator.start()
    }

    @Composable
    fun GetView(onPlayPaused: (isPaused: Boolean) -> Unit, onClosed: () -> Unit) {
        MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(context)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.
                    padding(16.dp, 0.dp)
            ) {
                Box(modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                    .fillMaxWidth(progressState.floatValue)
                    .height(4.dp)
                )
                Row (
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Box(
                        contentAlignment= Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                    ){
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
                            //internal circle with icon
                            Icon(
                                imageVector = if (isPausedIcon) {
                                     Icons.Filled.PlayArrow }
                                else {
                                    ImageVector.vectorResource(R.drawable.ic_baseline_pause_voice_message_24)
                                },
                                contentDescription = "contentDescription",
                                modifier = Modifier
                                    .size(24.dp)
                                ,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.size(16.dp))


                    Box(modifier = Modifier
                        .weight(.8f)
                        .align(Alignment.CenterVertically),
                       contentAlignment = Alignment.Center,
                    ){
                        Row {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "contentDescription",
                                modifier = Modifier
                                    .size(24.dp),
                                tint = Color.Gray
                            )

                            Text(name,
                                modifier = Modifier
                                    .align(Alignment.CenterVertically),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Box(
                        contentAlignment= Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterVertically),
                    ){
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

    // // Preview Logic
    // class DummyProvider : PreviewParameterProvider<String> {
    //     override val values: Sequence<String> = sequenceOf()
    // }
    // @Preview()
    // @PreviewParameter(DummyProvider::class)
    // @Composable
    // fun PreviewView() {
    //     GetView({ isPaused ->
    //
    //     },{
    //
    //     })
    // }
}
