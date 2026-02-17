/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.location.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextcloud.talk.R

@Composable
fun GeocodingResultItem(displayName: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Image(
            painter = painterResource(R.drawable.ic_circular_location),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
        )
        Text(
            text = displayName,
            fontSize = 18.sp,
            color = colorResource(R.color.high_emphasis_text),
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
        )
    }
}

@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewGeocodingResultItem() {
    MaterialTheme {
        GeocodingResultItem(
            displayName = "Sonnenallee 50, Rixdorf, Neukölln, Berlin, 12055, Deutschland",
            onClick = {}
        )
    }
}

@Preview(name = "RTL - Arabic", showBackground = true, locale = "ar")
@Composable
private fun PreviewGeocodingResultItemRtl() {
    MaterialTheme {
        GeocodingResultItem(
            displayName = "شارع الملك فهد، الرياض، المملكة العربية السعودية",
            onClick = {}
        )
    }
}
