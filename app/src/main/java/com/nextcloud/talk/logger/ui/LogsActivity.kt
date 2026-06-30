/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.logger.ui

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import autodagger.AutoInjector
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.BaseActivity
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.components.ColoredStatusBar
import com.nextcloud.talk.components.StandardAppBar
import com.nextcloud.talk.logger.Level
import com.nextcloud.talk.logger.LogEntry
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class LogsActivity : BaseActivity() {

    @Inject
    lateinit var viewModelFactory: ViewModelProvider.Factory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NextcloudTalkApplication.sharedApplication!!.componentApplication.inject(this)

        val viewModel = ViewModelProvider(this, viewModelFactory)[LogsViewModel::class.java]
        val colorScheme = viewThemeUtils.getColorScheme(this)

        setContent {
            MaterialTheme(colorScheme = colorScheme) {
                val entries = viewModel.entries.collectAsState().value
                val isLoading = viewModel.isLoading.collectAsState().value
                val totalSize = viewModel.totalSize.collectAsState().value
                val loggingEnabled = viewModel.loggingEnabled.collectAsState().value
                val advancedLogging = viewModel.advancedLogging.collectAsState().value

                val menuItems = listOf(
                    stringResource(R.string.nc_logs_delete_all) to { viewModel.deleteAll() }
                )

                ColoredStatusBar()
                Scaffold(
                    modifier = Modifier
                        .statusBarsPadding()
                        .displayCutoutPadding(),
                    topBar = {
                        StandardAppBar(
                            title = stringResource(R.string.nc_logs_title),
                            menuItems = menuItems
                        )
                    }
                ) { paddingValues ->
                    LogsContent(
                        state = LogsUiState(
                            entries = entries,
                            isLoading = isLoading,
                            totalSize = totalSize,
                            lostEntries = viewModel.lostEntries,
                            loggingEnabled = loggingEnabled,
                            advancedLogging = advancedLogging
                        ),
                        onLoggingEnabledChange = { viewModel.setLoggingEnabled(it) },
                        onAdvancedLoggingChange = { viewModel.setAdvancedLogging(it) },
                        onDisable = { deleteExisting -> viewModel.setLoggingEnabled(false, deleteExisting) },
                        paddingValues = paddingValues
                    )
                }

                LaunchedEffect(Unit) {
                    viewModel.load()
                }
            }
        }
    }
}

private data class LogsUiState(
    val entries: List<LogEntry>,
    val isLoading: Boolean,
    val totalSize: Long,
    val lostEntries: Boolean,
    val loggingEnabled: Boolean,
    val advancedLogging: Boolean
)

@Suppress("Detekt.LongMethod")
@Composable
private fun LogsContent(
    state: LogsUiState,
    onLoggingEnabledChange: (Boolean) -> Unit,
    onAdvancedLoggingChange: (Boolean) -> Unit,
    onDisable: (deleteExisting: Boolean) -> Unit,
    paddingValues: PaddingValues
) {
    val listState = rememberLazyListState()
    var showDisableDialog by remember { mutableStateOf(false) }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text(stringResource(R.string.nc_logs_disable_dialog_title)) },
            text = { Text(stringResource(R.string.nc_logs_disable_dialog_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDisableDialog = false
                    onDisable(true)
                }) {
                    Text(stringResource(R.string.nc_logs_disable_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDisableDialog = false
                    onDisable(false)
                }) {
                    Text(stringResource(R.string.nc_logs_disable_keep))
                }
            }
        )
    }

    LaunchedEffect(state.entries.size) {
        if (state.entries.isNotEmpty()) listState.scrollToItem(state.entries.size - 1)
    }
    Column(
        modifier = Modifier
            .background(colorResource(R.color.bg_default))
            .padding(paddingValues)
            .fillMaxSize()
    ) {
        LogsHeader(
            state = state,
            onLoggingEnabledChange = { enabled ->
                if (!enabled) showDisableDialog = true else onLoggingEnabledChange(true)
            },
            onAdvancedLoggingChange = onAdvancedLoggingChange
        )
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.nc_logs_empty))
            }
        } else {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                items(state.entries) { entry ->
                    LogEntryRow(entry)
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Suppress("Detekt.LongMethod")
@Composable
private fun LogsHeader(
    state: LogsUiState,
    onLoggingEnabledChange: (Boolean) -> Unit,
    onAdvancedLoggingChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.nc_logs_logging_enabled),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = state.loggingEnabled, onCheckedChange = onLoggingEnabledChange)
    }
    if (!state.loggingEnabled) {
        Text(
            text = stringResource(R.string.nc_logs_logging_disabled_note),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.nc_logs_advanced_logging),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(checked = state.advancedLogging, onCheckedChange = onAdvancedLoggingChange)
    }
    if (state.advancedLogging) {
        Text(
            text = stringResource(R.string.nc_logs_advanced_logging_enabled_warning),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    if (state.lostEntries) {
        Text(
            text = stringResource(R.string.nc_logs_overflow_warning),
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 12.sp
        )
    }
    if (state.totalSize > 0) {
        Text(
            text = stringResource(R.string.nc_logs_total_size, state.totalSize / BYTES_PER_KB),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private val displayFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.ROOT)

@Suppress("MagicNumber")
private val colorDebug = Color(0xFF4FC3F7)

@Suppress("MagicNumber")
private val colorInfo = Color(0xFF81C784)

@Suppress("MagicNumber")
private val colorWarning = Color(0xFFFFB74D)

@Suppress("MagicNumber")
private val colorError = Color(0xFFEF5350)

@Suppress("MagicNumber")
private val colorMuted = Color(0xFF888888)

@Suppress("Detekt.LongMethod")
@Composable
private fun LogEntryRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        Level.DEBUG -> colorDebug
        Level.INFO -> colorInfo
        Level.WARNING -> colorWarning
        Level.ERROR -> colorError
        Level.NONE -> colorMuted
    }
    val lines = entry.message.split('\n')
    val isMultiLine = lines.size > 1
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (isMultiLine) Modifier.clickable { expanded = !expanded } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(levelColor)
        )
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
            val meta = "${displayFormat.format(entry.timestamp)}  " +
                "${entry.pid}-${entry.tid}  ${entry.level.tag}/${entry.tag}"
            Text(
                text = meta,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = colorMuted
            )
            if (expanded) {
                Text(
                    text = entry.message,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = levelColor,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                )
            } else {
                Text(
                    text = lines.first(),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = levelColor
                )
            }
            if (isMultiLine && !expanded) {
                Text(
                    text = "▼ ${lines.size - 1} more lines",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = colorMuted
                )
            }
        }
    }
}

private const val BYTES_PER_KB = 1024
