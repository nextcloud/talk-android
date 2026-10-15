/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2021 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.fullscreenfile

import android.content.res.Configuration
import android.widget.TextView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nextcloud.talk.R
import com.nextcloud.talk.components.StandardAppBar
import io.noties.markwon.Markwon

/**
 * [text] is null while the file is still being read.
 */
@Composable
fun FullScreenTextScreen(title: String, text: String?, isMarkdown: Boolean, actions: FullScreenTextActions) {
    val context = LocalContext.current
    val menuItems = buildList {
        add(stringResource(R.string.share) to actions.onShare)
        add(stringResource(R.string.nc_save_message) to actions.onSave)
        if (actions.onOpenInFilesApp != null) {
            add(stringResource(R.string.open_in_files_app) to actions.onOpenInFilesApp)
        }
    }

    Scaffold(
        topBar = { StandardAppBar(title = title, menuItems = menuItems) },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        when {
            text == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            isMarkdown -> {
                val markwon = remember { Markwon.create(context) }
                AndroidView(
                    factory = { ctx -> TextView(ctx).apply { setTextIsSelectable(true) } },
                    update = { markwon.setMarkdown(it, text) },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                )
            }

            else -> {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 0.dp)
                    )
                }
            }
        }
    }
}

data class FullScreenTextActions(
    val onShare: () -> Unit,
    val onSave: () -> Unit,
    val onOpenInFilesApp: (() -> Unit)? = null
)

private const val PREVIEW_TEXT = """
# Heading

This is a sample paragraph with **bold** and *italic* text.

- Item one
- Item two
- Item three
"""

@Preview(name = "Light", showBackground = true)
@Composable
private fun PreviewFullScreenTextScreenLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        FullScreenTextScreen(
            title = "notes.md",
            text = PREVIEW_TEXT,
            isMarkdown = false,
            actions = FullScreenTextActions(onShare = {}, onSave = {})
        )
    }
}

@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PreviewFullScreenTextScreenDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        FullScreenTextScreen(
            title = "notes.md",
            text = PREVIEW_TEXT,
            isMarkdown = false,
            actions = FullScreenTextActions(onShare = {}, onSave = {})
        )
    }
}

@Preview(name = "RTL - Arabic", showBackground = true, locale = "ar")
@Composable
private fun PreviewFullScreenTextScreenRtl() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        FullScreenTextScreen(
            title = "ملاحظات.md",
            text = "هذا نص تجريبي باللغة العربية لاختبار تخطيط الواجهة من اليمين إلى اليسار.",
            isMarkdown = false,
            actions = FullScreenTextActions(onShare = {}, onSave = {})
        )
    }
}
