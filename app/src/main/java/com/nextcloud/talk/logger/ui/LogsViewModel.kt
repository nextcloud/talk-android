/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.logger.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import com.nextcloud.talk.logger.Level
import com.nextcloud.talk.logger.LogEntry
import com.nextcloud.talk.logger.LoggerImpl
import com.nextcloud.talk.logger.LogsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import androidx.core.content.edit
import com.nextcloud.talk.logger.Logger

class LogsViewModel @Inject constructor(private val logsRepository: LogsRepository, private val context: Context) :
    ViewModel() {

    @Inject
    lateinit var logger: Logger

    private val prefs by lazy {
        context.getSharedPreferences(LoggerImpl.PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries

    private val _totalSize = MutableStateFlow(0L)
    val totalSize: StateFlow<Long> = _totalSize

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _loggingEnabled = MutableStateFlow(logsRepository.minimumLevel != Level.NONE)
    val loggingEnabled: StateFlow<Boolean> = _loggingEnabled

    private val _advancedLogging = MutableStateFlow(logsRepository.minimumLevel <= Level.DEBUG)
    val advancedLogging: StateFlow<Boolean> = _advancedLogging

    val lostEntries: Boolean get() = logsRepository.lostEntries

    fun setLoggingEnabled(enabled: Boolean, deleteExisting: Boolean = false) {
        val level = if (enabled) Level.INFO else Level.NONE
        logsRepository.minimumLevel = level
        _loggingEnabled.value = enabled
        if (!enabled) _advancedLogging.value = false
        prefs.edit { putString(LoggerImpl.PREF_LOG_LEVEL, level.name) }
        if (!enabled && deleteExisting) {
            logsRepository.deleteAll()
            _entries.value = emptyList()
            _totalSize.value = 0L
        } else {
            load()
        }
    }

    fun setAdvancedLogging(enabled: Boolean) {
        val level = if (enabled) Level.DEBUG else Level.INFO
        logsRepository.minimumLevel = level
        _advancedLogging.value = enabled
        prefs.edit { putString(LoggerImpl.PREF_LOG_LEVEL, level.name) }
        logger.i(this::class.java.simpleName, "Advanced logging ${if (enabled) "enabled" else "disabled"}")
        load()
    }

    fun load() {
        _isLoading.value = true
        logsRepository.load { loaded, size ->
            _entries.value = loaded.sortedBy { it.timestamp }
            _totalSize.value = size
            _isLoading.value = false
        }
    }

    fun deleteAll() {
        logsRepository.deleteAll()
        _entries.value = emptyList()
        _totalSize.value = 0L
    }
}
