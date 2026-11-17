/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings.ui

import android.content.Context
import android.content.res.Configuration
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nextcloud.talk.R
import com.nextcloud.talk.settings.NotificationSound
import com.nextcloud.talk.settings.RingtoneState

/**
 * Lets the user choose the sound of [sound]. Besides the current sound and no sound, it offers the sound bundled with
 * the app, which the system ringtone picker doesn't list.
 */
@Composable
internal fun SoundDialog(
    sound: NotificationSound,
    current: RingtoneState,
    callbacks: SettingsScreenCallbacks,
    onDismiss: () -> Unit
) {
    val options = listOfNotNull(RingtoneState.Default, current as? RingtoneState.Custom, RingtoneState.Silent)
    var selectedIndex by rememberSaveable { mutableIntStateOf(options.indexOf(current)) }
    val previewPlayer = rememberSoundPreviewPlayer()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(sound.titleRes)) },
        text = {
            Column(modifier = Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                options.forEachIndexed { index, option ->
                    RadioOption(label = soundLabel(option), selected = index == selectedIndex, onClick = {
                        selectedIndex = index
                        previewPlayer.stop()
                        sound.uriOf(option)?.let { previewPlayer.play(it, sound.audioUsage) }
                    })
                }
                RadioOption(label = stringResource(R.string.nc_settings_other_sound), selected = false, onClick = {
                    previewPlayer.stop()
                    onDismiss()
                    callbacks.onPickNotificationSound(sound, current)
                })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                val selected = options.getOrNull(selectedIndex)
                if (selected != null && selected != current) {
                    callbacks.onNotificationSoundChange(sound, sound.uriOf(selected))
                }
            }) { Text(stringResource(R.string.nc_ok)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = {
                    onDismiss()
                    callbacks.onOpenSystemSettings(sound.systemSettingsScreen)
                }) {
                    Text(stringResource(R.string.nc_notification_settings))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.nc_cancel)) }
            }
        }
    )
}

@Composable
private fun soundLabel(ringtone: RingtoneState): String =
    when (ringtone) {
        RingtoneState.Default -> stringResource(R.string.nc_settings_default_ringtone)
        RingtoneState.Silent, RingtoneState.Disabled -> stringResource(R.string.nc_settings_no_ringtone)
        is RingtoneState.Custom -> ringtone.title
    }

/** Plays one sound at a time, to preview it. */
internal class SoundPreviewPlayer(private val context: Context) {
    private var ringtone: Ringtone? = null

    fun play(uri: String, audioUsage: Int) {
        stop()
        ringtone = RingtoneManager.getRingtone(context, uri.toUri())?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(audioUsage)
                .build()
            play()
        }
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
    }
}

/** A [SoundPreviewPlayer] that stops when it leaves the composition or the screen is paused. */
@Composable
internal fun rememberSoundPreviewPlayer(): SoundPreviewPlayer {
    val context = LocalContext.current
    val player = remember(context) { SoundPreviewPlayer(context) }
    DisposableEffect(player) {
        onDispose { player.stop() }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.stop() }
    return player
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SoundDialogPreview() {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
        SoundDialog(
            sound = NotificationSound.CALL,
            current = RingtoneState.Custom("Ring", "content://media/internal/audio/media/1"),
            callbacks = SettingsScreenCallbacks(),
            onDismiss = {}
        )
    }
}
