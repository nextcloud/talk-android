/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.ParticipantUiState
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.ui.ActorAvatarImage
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CharacterAvatarUtils
import com.nextcloud.talk.utils.DisplayUtils.isDarkModeOn

@Composable
fun AvatarWithFallback(participant: ParticipantUiState, displayName: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Guests and email participants have no avatar on the server, so theirs is drawn here
        val isGuest = Participant.ActorType.GUESTS == participant.actorType ||
            Participant.ActorType.EMAILS == participant.actorType
        val guestAvatar = if (isGuest) {
            CharacterAvatarUtils.guestAvatar(displayName, stringResource(R.string.nc_guest))
        } else {
            null
        }

        if (guestAvatar != null) {
            ActorAvatarImage(avatar = guestAvatar, modifier = Modifier.fillMaxSize())
        } else {
            val avatarUrl = getUrlForAvatar(participant = participant)
            if (avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = stringResource(R.string.avatar),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                FallbackAvatar(participant = participant)
            }
        }
    }
}

@Composable
private fun FallbackAvatar(participant: ParticipantUiState) {
    val initials = participant.nick!!
        .split(" ")
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .take(2)
        .joinToString("")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials.ifEmpty { "?" },
            color = Color.Black,
            fontSize = 24.sp
        )
    }
}

@Composable
fun getUrlForAvatar(participant: ParticipantUiState): String =
    if (participant.actorType == Participant.ActorType.FEDERATED) {
        ApiUtils.getUrlForFederatedAvatar(
            participant.baseUrl,
            participant.roomToken,
            participant.actorId!!,
            if (isDarkModeOn(LocalContext.current)) 1 else 0,
            true
        )
    } else {
        ApiUtils.getUrlForAvatar(
            participant.baseUrl,
            participant.actorId,
            true,
            darkMode = isDarkModeOn(LocalContext.current)
        )
    }
