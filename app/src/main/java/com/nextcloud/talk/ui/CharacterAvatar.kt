/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.ui

import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.R
import com.nextcloud.talk.extensions.loadSystemAvatar
import com.nextcloud.talk.utils.ActorAvatar

/**
 * Circular avatar drawn from a character, the Compose counterpart of [CharacterAvatarDrawable].
 *
 * Expects the single character resolved by [com.nextcloud.talk.utils.CharacterAvatarUtils]; the
 * character scales with the space the avatar is given, so callers only size the modifier.
 */
@Composable
fun CharacterAvatar(character: String, backgroundColor: Color, textColor: Color, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        // Sized to the shorter side and centered, so a circle stays a circle instead of being
        // stretched into a pill when the space the avatar is given is not square
        val diameter = minOf(maxWidth, maxHeight)
        val fontSize = with(LocalDensity.current) {
            (diameter * CharacterAvatarDrawable.TEXT_SIZE_RATIO).toSp()
        }

        Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = character,
                color = textColor,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The avatar of an actor the server has no avatar for, whichever kind
 * [com.nextcloud.talk.utils.CharacterAvatarUtils] resolved it to.
 */
@Composable
fun ActorAvatarImage(avatar: ActorAvatar, modifier: Modifier = Modifier) {
    when (avatar) {
        is ActorAvatar.Character -> CharacterAvatar(
            character = avatar.character,
            backgroundColor = colorResource(avatar.backgroundColor),
            textColor = colorResource(avatar.textColor),
            modifier = modifier
        )

        ActorAvatar.AppIcon -> AndroidView(
            factory = { context -> ImageView(context).apply { loadSystemAvatar() } },
            modifier = modifier
        )

        ActorAvatar.PersonIcon -> Image(
            painter = painterResource(R.drawable.account_circle_96dp),
            contentDescription = stringResource(R.string.user_avatar),
            modifier = modifier
        )
    }
}
