/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.util.Patterns
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration

@Composable
fun String.withLinks(linkColor: Color = MaterialTheme.colorScheme.primary): AnnotatedString =
    remember(this, linkColor) {
        buildAnnotatedString {
            val matcher = Patterns.WEB_URL.matcher(this@withLinks)
            var lastIndex = 0

            while (matcher.find()) {
                append(this@withLinks, lastIndex, matcher.start())

                val url = matcher.group()
                val actualUrl = if (UriUtils.hasHttpProtocolPrefixed(url)) url else "https://$url"

                val start = length
                append(url)

                addStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    length
                )

                addLink(
                    LinkAnnotation.Url(actualUrl),
                    start,
                    length
                )

                lastIndex = matcher.end()
            }

            append(this@withLinks, lastIndex, this@withLinks.length)
        }
    }
