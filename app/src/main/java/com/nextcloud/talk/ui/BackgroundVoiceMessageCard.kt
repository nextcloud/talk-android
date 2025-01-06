/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import autodagger.AutoInjector
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.ui.theme.ViewThemeUtils
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class BackgroundVoiceMessageSeekbarCard {

    // TODO impl onclick callbacks
    // TODO get avatar and name
    // TODO connect to the manager through the model, test it out.

    // .... I kinda suck. This is taking some time to impl, and im out of commission for a week.
    // eta prob valentines day

    @Inject
    lateinit var viewThemeUtils: ViewThemeUtils

    @Inject
    lateinit var context: Context

    init {
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)
    }

    @Composable
    fun GetView() {
        MaterialTheme(colorScheme = viewThemeUtils.getColorScheme(context)) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
            ) {
                Box(modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(8.dp))
                    .fillMaxWidth(0.51f)
                    .height(4.dp)
                )
                Row (
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Box(
                        contentAlignment= Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterVertically),
                    ){
                        //internal circle with icon
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "contentDescription",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp)
                            ,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.size(16.dp))


                    Box(modifier = Modifier
                        .weight(.8f),
                       contentAlignment = Alignment.Center,
                    ){
                        Row {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "contentDescription",
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(2.dp),
                                tint = Color.Gray
                            )

                            Text("John Smith",
                                modifier = Modifier
                                    .align(Alignment.CenterVertically),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Box(
                        contentAlignment= Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.CenterVertically),
                    ){
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "contentDescription",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(2.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                }
            }
        }
    }

    // Preview Logic
    class DummyProvider : PreviewParameterProvider<String> {
        override val values: Sequence<String> = sequenceOf()
    }
    @Preview()
    @PreviewParameter(DummyProvider::class)
    @Composable
    fun PreviewView() {
        GetView()
    }
}
