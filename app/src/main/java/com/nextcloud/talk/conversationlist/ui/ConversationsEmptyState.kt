/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.conversationlist.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R

/**
 * Top-level wrapper rendered by the empty_state_compose_view ComposeView.
 * Shows either the no-archived view, the generic empty view, or nothing.
 */
@Composable
fun ConversationsEmptyStateView(
    isListEmpty: Boolean,
    showNoArchivedView: Boolean,
    showLogo: Boolean,
    onCreateNewConversation: () -> Unit
) {
    when {
        showNoArchivedView -> NoArchivedConversationsView()
        isListEmpty -> EmptyConversationsView(showLogo = showLogo, onCreateNewConversation = onCreateNewConversation)
    }
}

@Composable
fun EmptyConversationsView(showLogo: Boolean, onCreateNewConversation: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCreateNewConversation() }
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (showLogo) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = stringResource(R.string.nc_app_product_name),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(colorResource(R.color.grey_600), BlendMode.SrcIn)
            )
        }

        Text(
            text = stringResource(R.string.nc_conversations_empty),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            color = colorResource(R.color.conversation_item_header),
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = stringResource(R.string.nc_conversations_empty_details),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            color = colorResource(R.color.textColorMaxContrast),
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun NoArchivedConversationsView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.outline_archive_24),
            contentDescription = stringResource(R.string.nc_app_product_name),
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(colorResource(R.color.grey_600), BlendMode.SrcIn)
        )

        Text(
            text = stringResource(R.string.no_conversations_archived),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 8.dp),
            color = colorResource(R.color.high_emphasis_text),
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
private fun EmptyConversationsWithLogoPreview() {
    EmptyConversationsView(showLogo = true, onCreateNewConversation = {})
}

@Preview(showBackground = true)
@Composable
private fun EmptyConversationsNoLogoPreview() {
    EmptyConversationsView(showLogo = false, onCreateNewConversation = {})
}

@Preview(showBackground = true)
@Composable
private fun NoArchivedConversationsPreview() {
    NoArchivedConversationsView()
}
