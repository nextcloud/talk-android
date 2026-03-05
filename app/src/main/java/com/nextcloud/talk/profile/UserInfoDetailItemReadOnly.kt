/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.profile

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R
import com.nextcloud.talk.models.json.userprofile.Scope

private const val EDGE_RADIUS = 16
private const val REGULAR_RADIUS = 4

@Composable
fun UserInfoDetailItemViewOnly(
    userInfo: UserInfoDetailItemData,
    position: UserInfoDetailItemPosition,
    ellipsize: Boolean = false
) {
    val cardShape = when (position) {
        UserInfoDetailItemPosition.FIRST -> RoundedCornerShape(
            topStart = EDGE_RADIUS.dp,
            topEnd = EDGE_RADIUS.dp,
            bottomStart = REGULAR_RADIUS.dp,
            bottomEnd = REGULAR_RADIUS.dp
        )
        UserInfoDetailItemPosition.MIDDLE -> RoundedCornerShape(REGULAR_RADIUS.dp)
        UserInfoDetailItemPosition.LAST -> RoundedCornerShape(
            topStart = REGULAR_RADIUS.dp,
            topEnd = REGULAR_RADIUS.dp,
            bottomStart = EDGE_RADIUS.dp,
            bottomEnd = EDGE_RADIUS.dp
        )
    }
    val topPadding = if (position == UserInfoDetailItemPosition.FIRST) 0.dp else 2.dp
    val bottomPadding = if (position == UserInfoDetailItemPosition.LAST) 16.dp else 0.dp
    Card(
        shape = cardShape,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = topPadding, end = 16.dp, bottom = bottomPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = userInfo.icon),
                contentDescription = userInfo.hint,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = userInfo.hint, style = MaterialTheme.typography.titleMedium)
                if (ellipsize) {
                    Text(
                        text = userInfo.text,
                        style = MaterialTheme.typography.bodyMedium,
                        overflow = TextOverflow.MiddleEllipsis,
                        maxLines = 1
                    )
                } else {
                    Text(text = userInfo.text, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (userInfo.scope != null) {
                ScopeIcon(userInfo.scope, userInfo.hint)
            }
        }
    }
}

@Composable
private fun ScopeIcon(scope: Scope, hint: String) {
    val scopeIconRes = when (scope) {
        Scope.PRIVATE -> R.drawable.ic_cellphone
        Scope.LOCAL -> R.drawable.ic_password
        Scope.FEDERATED -> R.drawable.ic_contacts
        Scope.PUBLISHED -> R.drawable.ic_link
    }
    Spacer(modifier = Modifier.width(16.dp))
    Icon(
        painter = painterResource(id = scopeIconRes),
        contentDescription = stringResource(R.string.scope_toggle_description, hint),
        modifier = Modifier.size(24.dp)
    )
}

@Preview(name = "Light · Read-only", showBackground = true)
@Preview(name = "Dark · Read-only", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewViewOnly() {
    val colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        Surface {
            Column {
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(
                        R.drawable.ic_user,
                        "John Doe",
                        stringResource(R.string.user_info_displayname),
                        Scope.PRIVATE
                    ),
                    position = UserInfoDetailItemPosition.FIRST
                )
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(
                        R.drawable.ic_phone,
                        "+49 123 456 789 12",
                        stringResource(R.string.user_info_phone),
                        Scope.LOCAL
                    ),
                    position = UserInfoDetailItemPosition.MIDDLE
                )
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(
                        R.drawable.ic_email,
                        "john@example.com",
                        stringResource(R.string.user_info_email),
                        Scope.FEDERATED
                    ),
                    position = UserInfoDetailItemPosition.MIDDLE
                )
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(
                        R.drawable.ic_map_marker,
                        "Berlin, Germany",
                        stringResource(R.string.user_info_address),
                        Scope.PUBLISHED
                    ),
                    position = UserInfoDetailItemPosition.MIDDLE
                )
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(
                        R.drawable.ic_web,
                        "nextcloud.com",
                        stringResource(R.string.user_info_website),
                        Scope.PRIVATE
                    ),
                    position = UserInfoDetailItemPosition.LAST
                )
            }
        }
    }
}

@Preview(name = "RTL · Arabic", showBackground = true, locale = "ar")
@Composable
private fun PreviewRtl() {
    MaterialTheme {
        Surface {
            Column {
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(R.drawable.ic_user, "جون دو", "الاسم الكامل", Scope.PRIVATE),
                    position = UserInfoDetailItemPosition.FIRST
                )
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(R.drawable.ic_phone, "٠١٢٣ ٤٥٦ ٧٨٩", "هاتف", Scope.LOCAL),
                    position = UserInfoDetailItemPosition.MIDDLE
                )
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(
                        R.drawable.ic_email,
                        "linda.de.long.family.name.to.render@example-length.com",
                        "بريد إلكتروني",
                        Scope.FEDERATED
                    ),
                    position = UserInfoDetailItemPosition.MIDDLE,
                    ellipsize = true
                )
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(
                        R.drawable.ic_map_marker,
                        "برلين، ألمانيا",
                        "العنوان",
                        Scope.PUBLISHED
                    ),
                    position = UserInfoDetailItemPosition.MIDDLE
                )
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(
                        R.drawable.ic_web,
                        "nextcloud.com",
                        "الموقع الإلكتروني",
                        Scope.PRIVATE
                    ),
                    position = UserInfoDetailItemPosition.MIDDLE
                )
                UserInfoDetailItemViewOnly(
                    userInfo = UserInfoDetailItemData(R.drawable.ic_twitter, "@nextcloud", "تويتر", Scope.LOCAL),
                    position = UserInfoDetailItemPosition.LAST
                )
            }
        }
    }
}
