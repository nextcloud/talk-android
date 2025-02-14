/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.diagnose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextcloud.talk.R

@Composable
fun DiagnoseContentComposable(data: State<List<DiagnoseActivity.DiagnoseElement>>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        data.value.forEach { element ->
            when (element) {
                is DiagnoseActivity.DiagnoseElement.DiagnoseHeadline -> Text(
                    modifier = Modifier.padding(vertical = 16.dp),
                    text = element.headline,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = LocalDensity.current.run { dimensionResource(R.dimen.headline_text_size).toPx().toSp() },
                    fontWeight = FontWeight.Bold
                )

                is DiagnoseActivity.DiagnoseElement.DiagnoseEntry -> {
                    Text(
                        text = element.key,
                        color = colorResource(R.color.high_emphasis_text),
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        modifier = Modifier.padding(bottom = 16.dp),
                        text = element.value,
                        color = colorResource(R.color.high_emphasis_text)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DiagnoseContentPreview() {
    val state = remember {
        mutableStateOf(
            listOf(
                DiagnoseActivity.DiagnoseElement.DiagnoseHeadline("Headline"),
                DiagnoseActivity.DiagnoseElement.DiagnoseEntry("Key", "Value")
            )
        )
    }
    DiagnoseContentComposable(state)
}
