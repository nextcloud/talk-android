/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.ui.chat

import android.widget.LinearLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.elyeproj.loaderviewlibrary.LoaderImageView
import com.elyeproj.loaderviewlibrary.LoaderTextView
import com.nextcloud.talk.R

private const val INT_8 = 8
private const val INT_128 = 128

@Composable
fun ShimmerGroup() {
    Shimmer()
    Shimmer(true)
    Shimmer()
    Shimmer(true)
    Shimmer(true)
    Shimmer()
    Shimmer(true)
}

@Composable
private fun Shimmer(outgoing: Boolean = false) {
    val outgoingColor = colorScheme.primary.toArgb()

    Row(modifier = Modifier.padding(top = 16.dp)) {
        if (!outgoing) {
            ShimmerImage(this)
        }

        val v1 by remember { mutableIntStateOf((INT_8..INT_128).random()) }
        val v2 by remember { mutableIntStateOf((INT_8..INT_128).random()) }
        val v3 by remember { mutableIntStateOf((INT_8..INT_128).random()) }

        Column {
            ShimmerText(this, v1, outgoing, outgoingColor)
            ShimmerText(this, v2, outgoing, outgoingColor)
            ShimmerText(this, v3, outgoing, outgoingColor)
        }
    }
}

@Composable
private fun ShimmerImage(rowScope: RowScope) {
    rowScope.apply {
        AndroidView(
            factory = { ctx ->
                LoaderImageView(ctx).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    val color = resources.getColor(R.color.nc_shimmer_default_color, null)
                    setBackgroundColor(color)
                }
            },
            modifier = Modifier
                .clip(CircleShape)
                .size(40.dp)
                .align(Alignment.Top)
        )
    }
}

@Composable
private fun ShimmerText(columnScope: ColumnScope, margin: Int, outgoing: Boolean = false, outgoingColor: Int) {
    columnScope.apply {
        AndroidView(
            factory = { ctx ->
                LoaderTextView(ctx).apply {
                    layoutParams =
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    val color = if (outgoing) {
                        outgoingColor
                    } else {
                        resources.getColor(R.color.nc_shimmer_default_color, null)
                    }

                    setBackgroundColor(color)
                }
            },
            modifier = Modifier.padding(
                top = 6.dp,
                end = if (!outgoing) margin.dp else 8.dp,
                start = if (outgoing) margin.dp else 8.dp
            )
        )
    }
}
