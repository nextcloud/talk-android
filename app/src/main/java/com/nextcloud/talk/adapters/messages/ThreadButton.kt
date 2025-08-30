/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.adapters.messages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

@Composable
fun ThreadButtonComposable(onButtonClick: () -> Unit = {}) {
    OutlinedButton(
        onClick = onButtonClick,
        modifier = Modifier
            .padding(8.dp)
            .height(22.dp)
            .width(32.dp),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(1.dp, colorResource(R.color.grey_600)),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.Transparent,
            contentColor = colorResource(R.color.grey_600)
        )
    ) {
        Icon(
            painter = painterResource(R.drawable.outline_forum_24),
            contentDescription = stringResource(R.string.open_thread),
            modifier = Modifier.size(16.dp),
            tint = colorResource(R.color.grey_600)
        )
    }
}

@Preview
@Composable
fun ThreadButtonPreview() {
    ThreadButtonComposable()
}
