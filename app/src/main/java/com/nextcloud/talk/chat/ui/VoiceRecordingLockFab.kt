/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R

@Composable
fun VoiceRecordingLockFab(visible: Boolean, offsetY: Float, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        FloatingActionButton(
            onClick = {},
            modifier = Modifier.graphicsLayer { translationY = offsetY },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_lock_open_grey600_24dp),
                contentDescription = stringResource(R.string.continuous_voice_message_recording)
            )
        }
    }
}

private const val PREVIEW_DRAG_OFFSET_PX = -80f

@Preview(name = "Visible · default position · Light")
@Preview(name = "Visible · default position · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VisibleDefaultPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            VoiceRecordingLockFab(visible = true, offsetY = 0f)
        }
    }
}

@Preview(name = "Visible · mid-drag · Light")
@Preview(name = "Visible · mid-drag · Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VisibleDraggedPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            VoiceRecordingLockFab(visible = true, offsetY = PREVIEW_DRAG_OFFSET_PX)
        }
    }
}

@Preview(name = "Hidden · Light")
@Composable
private fun HiddenPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            VoiceRecordingLockFab(visible = false, offsetY = 0f)
        }
    }
}
