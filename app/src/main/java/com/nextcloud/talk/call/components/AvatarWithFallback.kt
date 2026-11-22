/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.ParticipantUiState
import com.nextcloud.talk.models.json.participants.ParticipantDto
import com.nextcloud.talk.ui.ActorAvatarImage
import com.nextcloud.talk.ui.LocalImageAuthHeader
import com.nextcloud.talk.ui.withAuthHeader
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
        val isGuest = ParticipantDto.ActorType.GUESTS == participant.actorType ||
            ParticipantDto.ActorType.EMAILS == participant.actorType
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
                val authHeader = LocalImageAuthHeader.current.takeIf {
                    participant.actorType == ParticipantDto.ActorType.FEDERATED
                }
                val fallback = painterResource(R.drawable.account_circle_96dp)
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .withAuthHeader(authHeader)
                        .build(),
                    contentDescription = stringResource(R.string.avatar),
                    contentScale = ContentScale.Crop,
                    placeholder = fallback,
                    error = fallback,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                FallbackAvatar()
            }
        }
    }
}

@Composable
private fun FallbackAvatar() =
    Image(
        painter = painterResource(R.drawable.account_circle_96dp),
        contentDescription = stringResource(R.string.avatar),
        modifier = Modifier.fillMaxSize()
    )

@Composable
fun getUrlForAvatar(participant: ParticipantUiState): String =
    if (participant.actorType == ParticipantDto.ActorType.FEDERATED) {
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
