/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.utils.DisplayUtils

private const val MAX_NAME_LENGTH = 14
private const val ANIMATION_DURATION_MS = 200
private const val MAX_NAMED_PARTICIPANTS = 3
private const val FOUR_PARTICIPANTS = 4

private data class TypingStrings(
    val isTyping: String,
    val areTyping: String,
    val and: String,
    val typing1Other: String,
    val typingXOthers: String
)

@Composable
fun TypingIndicatorBanner(names: List<String>, modifier: Modifier = Modifier) {
    val strings = TypingStrings(
        isTyping = stringResource(R.string.typing_is_typing),
        areTyping = stringResource(R.string.typing_are_typing),
        and = stringResource(R.string.nc_common_and),
        typing1Other = stringResource(R.string.typing_1_other),
        typingXOthers = stringResource(R.string.typing_x_others)
    )

    val text = remember(names, strings) {
        buildTypingAnnotatedString(names, strings)
    }

    AnimatedVisibility(
        visible = names.isNotEmpty(),
        modifier = modifier,
        enter = slideInVertically(tween(ANIMATION_DURATION_MS)) { it },
        exit = slideOutVertically(tween(ANIMATION_DURATION_MS)) { it }
    ) {
        Text(
            text = text,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp, vertical = 1.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun buildTypingAnnotatedString(names: List<String>, s: TypingStrings): AnnotatedString {
    fun ellipsize(name: String) = DisplayUtils.ellipsize(name, MAX_NAME_LENGTH)
    fun bold(builder: AnnotatedString.Builder, text: String) {
        builder.withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text) }
    }

    return buildAnnotatedString {
        when (names.size) {
            0 -> Unit

            // Alice is typing …
            1 -> {
                bold(this, ellipsize(names[0]))
                append(" ${s.isTyping}")
            }

            // Alice and Bob are typing …
            2 -> {
                bold(this, ellipsize(names[0]))
                append(" ${s.and} ")
                bold(this, ellipsize(names[1]))
                append(" ${s.areTyping}")
            }

            // Alice, Bob and Carol are typing …
            MAX_NAMED_PARTICIPANTS -> {
                bold(this, ellipsize(names[0]))
                append(", ")
                bold(this, ellipsize(names[1]))
                append(" ${s.and} ")
                bold(this, ellipsize(names[2]))
                append(" ${s.areTyping}")
            }

            // Alice, Bob, Carol and 1 other is typing …
            FOUR_PARTICIPANTS -> {
                bold(this, names[0])
                append(", ")
                bold(this, names[1])
                append(", ")
                bold(this, names[2])
                append(" ${s.typing1Other}")
            }

            // Alice, Bob, Carol and N others are typing …
            else -> {
                val moreAmount = names.size - MAX_NAMED_PARTICIPANTS
                bold(this, names[0])
                append(", ")
                bold(this, names[1])
                append(", ")
                bold(this, names[2])
                append(" ${String.format(s.typingXOthers, moreAmount)}")
            }
        }
    }
}

private const val PREVIEW_WIDTH_DP = 360

@Preview(name = "1 participant · Light", widthDp = PREVIEW_WIDTH_DP)
@Preview(name = "1 participant · Dark", widthDp = PREVIEW_WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OneParticipantPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            TypingIndicatorBanner(names = listOf("Marcel"))
        }
    }
}

@Preview(name = "1 participant · RTL / Arabic", widthDp = PREVIEW_WIDTH_DP, locale = "ar")
@Composable
private fun OneParticipantRtlPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            TypingIndicatorBanner(names = listOf("مارسيل"))
        }
    }
}

@Preview(name = "2 participants · Light", widthDp = PREVIEW_WIDTH_DP)
@Preview(name = "2 participants · Dark", widthDp = PREVIEW_WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TwoParticipantsPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            TypingIndicatorBanner(names = listOf("Marcel", "Julius"))
        }
    }
}

@Preview(name = "2 participants · RTL / Arabic", widthDp = PREVIEW_WIDTH_DP, locale = "ar")
@Composable
private fun TwoParticipantsRtlPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            TypingIndicatorBanner(names = listOf("مارسيل", "يوليوس"))
        }
    }
}

@Preview(name = "3 participants · Light", widthDp = PREVIEW_WIDTH_DP)
@Preview(name = "3 participants · Dark", widthDp = PREVIEW_WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreeParticipantsPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            TypingIndicatorBanner(names = listOf("Marcel", "Julius", "Andy"))
        }
    }
}

@Preview(name = "3 participants · RTL / Arabic", widthDp = PREVIEW_WIDTH_DP, locale = "ar")
@Composable
private fun ThreeParticipantsRtlPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            TypingIndicatorBanner(names = listOf("مارسيل", "يوليوس", "أندي"))
        }
    }
}

@Preview(name = "5+ participants · Light", widthDp = PREVIEW_WIDTH_DP)
@Preview(name = "5+ participants · Dark", widthDp = PREVIEW_WIDTH_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ManyParticipantsPreview() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            TypingIndicatorBanner(names = listOf("Marcel", "Julius", "Andy", "Alice", "Bob"))
        }
    }
}

@Preview(name = "5+ participants · RTL / Arabic", widthDp = PREVIEW_WIDTH_DP, locale = "ar")
@Composable
private fun ManyParticipantsRtlPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            TypingIndicatorBanner(names = listOf("مارسيل", "يوليوس", "أندي", "أليس", "بوب"))
        }
    }
}

@Preview(name = "Hidden (no one typing) · Light", widthDp = PREVIEW_WIDTH_DP)
@Composable
private fun HiddenPreview() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface {
            TypingIndicatorBanner(names = emptyList())
        }
    }
}
