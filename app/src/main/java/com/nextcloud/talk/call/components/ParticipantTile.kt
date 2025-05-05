/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.call.ParticipantUiState

@Composable
fun ParticipantTile(participant: ParticipantUiState) {
    Box(
        modifier = Modifier
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.DarkGray)
    ) {
        if (participant.isStreamEnabled && participant.surfaceViewRenderer != null) {
            WebRTCVideoView(participant.surfaceViewRenderer)
        } else {
            AvatarWithFallback(participant)
        }

        if (participant.raisedHand) {
            Icon(
                painter = painterResource(id = R.drawable.ic_hand_back_left),
                contentDescription = "Raised Hand",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp),
                tint = Color.White
            )
        }

        if (!participant.isAudioEnabled) {
            Icon(
                painter = painterResource(id = R.drawable.ic_mic_off_white_24px),
                contentDescription = "Mic Off",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp),
                tint = Color.White
            )
        }

        Text(
            text = participant.nick,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(6.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

// @Composable
// fun ParticipantItem(participant: ParticipantDisplayItem) {
//     val context = LocalContext.current
//     val videoTrack = participant.mediaStream?.videoTracks?.firstOrNull()
//
//     Box(
//         modifier = Modifier
//             .aspectRatio(1f)
//             .background(Color.Black)
//             .padding(4.dp)
//     ) {
//         // Renderer
//         participant.surfaceViewRenderer?.let { renderer ->
//             AndroidView(
//                 factory = {
//                     // If not yet initialized
//                     if (renderer.parent != null) {
//                         (renderer.parent as? ViewGroup)?.removeView(renderer)
//                     }
//
//                     // if (!renderer.isInitialized) {  // TODO
//                         renderer.init(participant.rootEglBase.eglBaseContext, null)
//                         renderer.setMirror(false)
//                         renderer.setZOrderMediaOverlay(false)
//                         renderer.setEnableHardwareScaler(false)
//                         renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT)
//                     // }
//
//                     // Attach sink
//                     try {
//                         videoTrack?.removeSink(renderer)
//                     } catch (_: Exception) {}
//                     videoTrack?.addSink(renderer)
//
//                     renderer
//                 },
//                 modifier = Modifier.fillMaxSize(),
//                 update = { view ->
//                     view.visibility =
//                         if (videoTrack != null && participant.isConnected) View.VISIBLE else View.INVISIBLE
//                 }
//             )
//         }
//
//         // Overlay: Nick or Avatar
//         if (videoTrack == null || !participant.isConnected) {
//             Column(
//                 modifier = Modifier
//                     .fillMaxSize()
//                     .background(Color.DarkGray)
//                     .padding(8.dp),
//                 verticalArrangement = Arrangement.Center,
//                 horizontalAlignment = Alignment.CenterHorizontally
//             ) {
//                 Text(
//                     text = participant.nick!!,
//                     color = Color.White,
//                     fontSize = 16.sp,
//                     modifier = Modifier.padding(bottom = 8.dp)
//                 )
//                 // Replace this with image loader like Coil if needed
//                 Icon(
//                     imageVector = Icons.Default.Person,
//                     contentDescription = null,
//                     tint = Color.White,
//                     modifier = Modifier.size(40.dp)
//                 )
//             }
//         }
//
//         // Status indicators (audio muted / raised hand)
//         Row(
//             modifier = Modifier
//                 .align(Alignment.TopEnd)
//                 .padding(4.dp)
//         ) {
//             if (!participant.isAudioEnabled) {
//                 Icon(
//                     painter = painterResource(id = R.drawable.account_circle_96dp),
//                     contentDescription = "Mic Off",
//                     tint = Color.Red,
//                     modifier = Modifier.size(20.dp)
//                 )
//             }
//             if (participant.raisedHand?.state == true) {
//                 Icon(
//                     painter = painterResource(id = R.drawable.ic_hand_back_left),
//                     contentDescription = "Hand Raised",
//                     tint = Color.Yellow,
//                     modifier = Modifier.size(20.dp)
//                 )
//             }
//         }
//
//         // Loading spinner
//         if (!participant.isConnected) {
//             CircularProgressIndicator(
//                 modifier = Modifier.align(Alignment.Center)
//             )
//         }
//     }
// }
