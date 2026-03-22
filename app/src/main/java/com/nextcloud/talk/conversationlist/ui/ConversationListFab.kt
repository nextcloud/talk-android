/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.nextcloud.talk.R

private const val DISABLED_ALPHA = 0.38f
private const val FAB_ANIM_DURATION = 200

@Composable
fun ConversationListFab(isVisible: Boolean, isEnabled: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = isVisible,
        enter = scaleIn(animationSpec = tween(FAB_ANIM_DURATION)) + fadeIn(animationSpec = tween(FAB_ANIM_DURATION)),
        exit = scaleOut(animationSpec = tween(FAB_ANIM_DURATION)) + fadeOut(animationSpec = tween(FAB_ANIM_DURATION))
    ) {
        FloatingActionButton(
            onClick = { if (isEnabled) onClick() },
            modifier = Modifier.alpha(if (isEnabled) 1f else DISABLED_ALPHA)
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_pencil_grey600_24dp),
                contentDescription = stringResource(R.string.nc_new_conversation)
            )
        }
    }
}

@Composable
fun UnreadMentionBubble(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(dimensionResource(R.dimen.button_corner_radius))
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_baseline_arrow_downward_24px),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(modifier = Modifier.width(dimensionResource(R.dimen.standard_padding)))
            Text(
                text = stringResource(R.string.nc_new_mention),
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConversationListFabEnabledPreview() {
    ConversationListFab(isVisible = true, isEnabled = true, onClick = {})
}

@Preview(showBackground = true)
@Composable
private fun ConversationListFabDisabledPreview() {
    ConversationListFab(isVisible = true, isEnabled = false, onClick = {})
}

@Preview(showBackground = true)
@Composable
private fun UnreadMentionBubbleVisiblePreview() {
    UnreadMentionBubble(visible = true, onClick = {})
}
