/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.diagnose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nextcloud.talk.R

@Composable
fun DiagnoseContentComposable(
    data: State<List<DiagnoseActivity.DiagnoseElement>>,
    diagnoseViewModel: DiagnoseViewModel
) {
    val context = LocalContext.current
    val message = diagnoseViewModel.notificationMessage
    val isLoading = diagnoseViewModel.isLoading
    val showDialog = diagnoseViewModel.showDialog
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        data.value.forEach { element ->
            when (element) {
                is DiagnoseActivity.DiagnoseElement.DiagnoseHeadline -> {
                    if (element.headline == "Test push notifications") {
                        Button(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(vertical = 8.dp),
                            onClick = {
                                diagnoseViewModel.fetchTestPushResult()
                            }
                        ) {
                            Text(
                                text = element.headline,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                color = colorResource(R.color.high_emphasis_text),
                                fontSize = LocalDensity.current.run {
                                    dimensionResource(R.dimen.headline_text_size).toPx().toSp()
                                },
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        Text(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            text = element.headline,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = LocalDensity.current.run {
                                dimensionResource(R.dimen.headline_text_size).toPx().toSp()
                            },
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

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
        if (isLoading.value) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        if (showDialog.value) {
            Dialog(
                onDismissRequest = { diagnoseViewModel.dismissDialog() },
                properties = DialogProperties(
                    dismissOnClickOutside = true,
                    usePlatformDefaultWidth = false
                )
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    tonalElevation = 8.dp,
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.nc_test_results),
                            style = MaterialTheme.typography
                                .titleMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                modifier = Modifier.padding(top = 8.dp),
                                text = message.value
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(onClick = { diagnoseViewModel.dismissDialog() }) {
                                Text(text = stringResource(R.string.nc_cancel))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Push Message", message.value)
                                clipboard.setPrimaryClip(clip)
                                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                                    Toast.makeText(context, R.string.message_copied, Toast.LENGTH_SHORT).show()
                                }
                                diagnoseViewModel.dismissDialog()
                            }) {
                                Text(text = stringResource(R.string.nc_common_copy))
                            }
                        }
                    }
                }
            }
        }
    }
}
