/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chooseaccount

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.nextcloud.talk.R
import com.nextcloud.talk.contacts.loadImage
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.utils.ApiUtils

@Composable
fun ChooseAccountShareToContent(
    currentUser: User?,
    otherUsers: List<User>,
    onCurrentUserClick: () -> Unit,
    onOtherUserClick: (User) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth()
        ) {
            currentUser?.let {
                stickyHeader {
                    CurrentAccountRow(user = currentUser, onClick = onCurrentUserClick)
                }
            }
            items(otherUsers) { user ->
                OtherAccountRow(user = user, onClick = { onOtherUserClick(user) })
            }
        }
    }
}

@Composable
private fun CurrentAccountRow(user: User, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(4.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = loadImage(
                ApiUtils.getUrlForAvatar(user.baseUrl, user.userId, true),
                context,
                R.drawable.account_circle_48dp
            ),
            contentDescription = stringResource(R.string.avatar),
            modifier = Modifier
                .padding(start = 12.dp)
                .size(48.dp)
        )
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
        ) {
            Text(
                text = user.displayName ?: user.username ?: "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = user.baseUrl?.toUri()?.host ?: user.baseUrl ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.textColorMaxContrast),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_check_circle),
            contentDescription = stringResource(R.string.nc_account_chooser_active_user),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = 10.dp)
                .size(32.dp)
        )
    }
}

@Composable
private fun OtherAccountRow(user: User, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(4.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = loadImage(
                ApiUtils.getUrlForAvatar(user.baseUrl, user.userId, true),
                context,
                R.drawable.account_circle_48dp
            ),
            contentDescription = stringResource(R.string.avatar),
            modifier = Modifier
                .padding(start = 12.dp)
                .size(48.dp)
        )
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
        ) {
            Text(
                text = user.displayName ?: user.username ?: "",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = user.baseUrl?.toUri()?.host ?: user.baseUrl ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = colorResource(R.color.textColorMaxContrast),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Preview(name = "Light Mode", showBackground = true)
@Preview(
    name = "Dark Mode",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
@Preview(
    name = "R-t-L",
    showBackground = true,
    locale = "ar"
)
@Composable
private fun ChooseAccountShareToContentPreview() {
    val sampleCurrentUser = User(
        userId = "alice",
        username = "alice",
        baseUrl = "https://cloud.example.com",
        displayName = "Alice Example"
    )
    val sampleOtherUsers = listOf(
        User(
            userId = "bob",
            username = "bob",
            baseUrl = "https://nextcloud.example.org",
            displayName = "Bob Smith"
        ),
        User(
            userId = "carol",
            username = "carol",
            baseUrl = "https://nc.example.net",
            displayName = "Carol Jones"
        )
    )
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            ChooseAccountShareToContent(
                currentUser = sampleCurrentUser,
                otherUsers = sampleOtherUsers,
                onCurrentUserClick = {},
                onOtherUserClick = {}
            )
        }
    }
}
