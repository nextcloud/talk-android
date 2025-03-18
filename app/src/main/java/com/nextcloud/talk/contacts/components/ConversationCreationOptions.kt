/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.contacts.components

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R
import com.nextcloud.talk.conversationcreation.ConversationCreationActivity
import com.nextcloud.talk.openconversations.ListOpenConversationsActivity

@Composable
fun ConversationCreationOptions() {
    val context = LocalContext.current
    Column {
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                .clickable {
                    val intent = Intent(context, ConversationCreationActivity::class.java)
                    context.startActivity(intent)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.baseline_chat_bubble_outline_24),
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .padding(8.dp),
                contentDescription = null
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = stringResource(R.string.nc_create_new_conversation),
                maxLines = 1,
                fontSize = 16.sp
            )
        }
        Row(
            modifier = Modifier
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                .clickable {
                    val intent = Intent(context, ListOpenConversationsActivity::class.java)
                    context.startActivity(intent)
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.List,
                modifier = Modifier
                    .width(40.dp)
                    .height(40.dp)
                    .padding(8.dp),
                contentDescription = null
            )
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight(),
                text = stringResource(R.string.nc_join_open_conversations),
                fontSize = 16.sp
            )
        }
    }
}
