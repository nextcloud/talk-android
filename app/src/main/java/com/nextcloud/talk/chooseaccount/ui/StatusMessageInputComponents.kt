/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chooseaccount.ui

import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.getSystemService
import com.nextcloud.talk.R
import com.vanniktech.emoji.EmojiEditText
import com.vanniktech.emoji.EmojiPopup
import com.vanniktech.emoji.installDisableKeyboardInput
import com.vanniktech.emoji.installForceSingleEmoji

@Composable
internal fun EmojiAndMessageRow(
    emoji: String,
    message: String,
    onEmojiSelected: (String) -> Unit,
    onMessageChanged: (String) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        EmojiButton(emoji = emoji, onEmojiSelected = onEmojiSelected)
        Spacer(modifier = Modifier.width(8.dp))
        OutlinedTextField(
            value = message,
            onValueChange = onMessageChanged,
            modifier = Modifier.weight(1f),
            label = { Text(stringResource(R.string.whats_your_status)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )
    }
}

@Composable
private fun EmojiButton(emoji: String, onEmojiSelected: (String) -> Unit) {
    val isPreview = LocalInspectionMode.current
    val rootView = LocalView.current
    var emojiPopup by remember { mutableStateOf<EmojiPopup?>(null) }
    val displayEmoji = emoji.ifEmpty { stringResource(R.string.default_emoji) }

    Card(
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .size(56.dp)
            .clickable { emojiPopup?.show() }
    ) {
        if (isPreview) {
            Text(
                text = displayEmoji,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .size(56.dp)
                    .padding(14.dp)
            )
        } else {
            AndroidView(
                modifier = Modifier.size(56.dp),
                factory = { ctx ->
                    EmojiEditText(ctx).apply {
                        setText(displayEmoji)
                        gravity = Gravity.CENTER
                        textSize = EMOJI_TEXT_SIZE_SP
                        background = null
                        isCursorVisible = false
                        val popup = EmojiPopup(
                            rootView = rootView,
                            editText = this,
                            onEmojiClickListener = {
                                onEmojiSelected(text.toString())
                                emojiPopup?.dismiss()
                                clearFocus()
                                ctx.getSystemService<InputMethodManager>()
                                    ?.hideSoftInputFromWindow(windowToken, 0)
                            }
                        )
                        installDisableKeyboardInput(popup)
                        installForceSingleEmoji()
                        emojiPopup = popup
                    }
                },
                update = { view ->
                    if (view.text.toString() != displayEmoji) {
                        view.setText(displayEmoji)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClearAfterDropdown(selectedPosition: Int, onPositionSelected: (Int) -> Unit) {
    val options = listOf(
        stringResource(R.string.dontClear),
        stringResource(R.string.fifteenMinutes),
        stringResource(R.string.thirtyMinutes),
        stringResource(R.string.oneHour),
        stringResource(R.string.fourHours),
        stringResource(R.string.today),
        stringResource(R.string.thisWeek)
    )
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.clear_status_message_after),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = options[selectedPosition],
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                shape = RoundedCornerShape(12.dp)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEachIndexed { index, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onPositionSelected(index)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun ActionButtons(onClear: () -> Unit, onSet: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                text = stringResource(R.string.clear_status_message),
                textAlign = TextAlign.Center
            )
        }
        Button(
            onClick = onSet,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            Text(
                text = stringResource(R.string.set_status_message),
                textAlign = TextAlign.Center
            )
        }
    }
}

private const val EMOJI_TEXT_SIZE_SP = 24f
