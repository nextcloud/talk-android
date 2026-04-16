/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chooseaccount.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.chooseaccount.data.StatusRepository
import com.nextcloud.talk.models.json.status.ClearAt
import com.nextcloud.talk.models.json.status.Status
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.CapabilitiesUtil
import com.nextcloud.talk.utils.database.user.CurrentUserProviderOld
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

private const val ONE_SECOND_IN_MILLIS = 1000L
private const val ONE_MINUTE_IN_SECONDS = 60L
private const val FIFTEEN_MINUTES = 15L
private const val THIRTY_MINUTES = 30L
private const val FOUR_HOURS = 4L
private const val LAST_HOUR_OF_DAY = 23
private const val LAST_MINUTE_OF_HOUR = 59
private const val LAST_SECOND_OF_MINUTE = 59
private const val HTTP_STATUS_CODE_OK = 200
private const val HTTP_STATUS_CODE_NOT_FOUND = 404

class StatusMessageViewModel @Inject constructor(
    private val repository: StatusRepository,
    private val currentUserProvider: CurrentUserProviderOld
) : ViewModel() {

    private val currentUser = currentUserProvider.currentUser.blockingGet()
    private val credentials = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!

    private val _emoji = MutableStateFlow("")
    val emoji: StateFlow<String> = _emoji

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message

    private val _clearAtPosition = MutableStateFlow(0)
    val clearAtPosition: StateFlow<Int> = _clearAtPosition

    private val _predefinedStatuses = MutableStateFlow<List<PredefinedStatus>>(emptyList())
    val predefinedStatuses: StateFlow<List<PredefinedStatus>> = _predefinedStatuses

    private val _selectedPredefinedStatus = MutableStateFlow<PredefinedStatus?>(null)
    val selectedPredefinedStatus: StateFlow<PredefinedStatus?> = _selectedPredefinedStatus

    private val _isBackupStatusAvailable = MutableStateFlow(false)
    val isBackupStatusAvailable: StateFlow<Boolean> = _isBackupStatusAvailable

    private val _isDismissed = MutableStateFlow(false)
    val isDismissed: StateFlow<Boolean> = _isDismissed

    private var clearAt: Long? = null
    private var currentStatusMessageId: String? = null

    fun init(currentStatus: Status) {
        _emoji.value = currentStatus.icon ?: ""
        _message.value = currentStatus.message?.trim() ?: ""
        currentStatusMessageId = currentStatus.messageId
        clearAt = if (currentStatus.clearAt > 0) currentStatus.clearAt else null
        _isDismissed.value = false
        _predefinedStatuses.value = emptyList()
        _isBackupStatusAvailable.value = false
        _selectedPredefinedStatus.value = null
    }

    fun resetDismissed() {
        _isDismissed.value = false
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun fetchPredefinedStatuses() {
        viewModelScope.launch {
            try {
                val statuses = repository.getPredefinedStatuses(
                    credentials,
                    ApiUtils.getUrlForPredefinedStatuses(currentUser.baseUrl!!)
                )
                _predefinedStatuses.value = statuses

                if (_selectedPredefinedStatus.value == null && currentStatusMessageId?.isNotEmpty() == true) {
                    _selectedPredefinedStatus.value = statuses.firstOrNull { it.id == currentStatusMessageId }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while fetching predefined statuses", e)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun checkBackupStatus() {
        if (!CapabilitiesUtil.isRestoreStatusAvailable(currentUser)) return
        viewModelScope.launch {
            try {
                val statusOverall = repository.getBackupStatus(
                    credentials,
                    ApiUtils.getUrlForBackupStatus(currentUser.baseUrl!!, currentUser.userId!!)
                )
                if (statusOverall.ocs?.meta?.statusCode == HTTP_STATUS_CODE_OK) {
                    val backupStatus = statusOverall.ocs?.data ?: return@launch
                    if (backupStatus.message != null) {
                        val backupPredefined = PredefinedStatus(
                            id = backupStatus.userId!!,
                            icon = backupStatus.icon,
                            message = backupStatus.message!!,
                            clearAt = ClearAt(type = "period", time = backupStatus.clearAt.toString())
                        )
                        val updated = listOf(backupPredefined) + _predefinedStatuses.value
                        _predefinedStatuses.value = updated
                        _isBackupStatusAvailable.value = true
                    }
                }
            } catch (e: Exception) {
                val isNotFound = e.message?.contains(HTTP_STATUS_CODE_NOT_FOUND.toString()) == true
                if (isNotFound) {
                    Log.d(TAG, "User does not have a backup status set")
                } else {
                    Log.e(TAG, "Error while getting user backup status", e)
                }
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun clearStatus() {
        viewModelScope.launch {
            try {
                repository.clearStatusMessage(
                    credentials,
                    ApiUtils.getUrlForStatusMessage(currentUser.baseUrl!!)
                )
                _isDismissed.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clear status", e)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught", "ComplexMethod")
    fun setStatus() {
        val inputText = _message.value.ifEmpty { "" }
        val statusIcon = _emoji.value.ifEmpty { null }
        val selected = _selectedPredefinedStatus.value

        viewModelScope.launch {
            try {
                if (selected == null || selected.message != inputText || selected.icon != _emoji.value) {
                    repository.setCustomStatusMessage(
                        credentials,
                        ApiUtils.getUrlForSetCustomStatus(currentUser.baseUrl!!),
                        statusIcon,
                        inputText,
                        clearAt
                    )
                } else {
                    repository.setPredefinedStatusMessage(
                        credentials,
                        ApiUtils.getUrlForSetPredefinedStatus(currentUser.baseUrl!!),
                        selected.id,
                        clearAt
                    )
                }
                _isDismissed.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set status message", e)
            }
        }
    }

    @Suppress("Detekt.TooGenericExceptionCaught")
    fun revertStatus() {
        viewModelScope.launch {
            try {
                repository.revertStatus(
                    credentials,
                    ApiUtils.getUrlForRevertStatus(currentUser.baseUrl!!, currentStatusMessageId)
                )
                _isBackupStatusAvailable.value = false
                val updated = _predefinedStatuses.value.drop(1)
                _predefinedStatuses.value = updated
                _isDismissed.value = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to revert status", e)
            }
        }
    }

    fun selectPredefinedStatus(status: PredefinedStatus) {
        _selectedPredefinedStatus.value = status
        _emoji.value = status.icon ?: ""
        _message.value = status.message
        clearAt = clearAtToUnixTime(status.clearAt)
        _clearAtPosition.value = clearAtToClearAtPosition(status.clearAt)
    }

    fun updateEmoji(newEmoji: String) {
        _emoji.value = newEmoji
    }

    fun updateMessage(newMessage: String) {
        _message.value = newMessage
    }

    fun updateClearAtPosition(position: Int) {
        _clearAtPosition.value = position
        clearAt = statusMessageClearAtFromPosition(position)
    }

    companion object {
        private val TAG = StatusMessageViewModel::class.simpleName
        const val CLEAR_AT_POS_DONT_CLEAR = 0
        const val CLEAR_AT_POS_FIFTEEN_MINUTES = 1
        const val CLEAR_AT_POS_HALF_AN_HOUR = 2
        const val CLEAR_AT_POS_AN_HOUR = 3
        const val CLEAR_AT_POS_FOUR_HOURS = 4
        const val CLEAR_AT_POS_TODAY = 5
        const val CLEAR_AT_POS_END_OF_WEEK = 6
    }
}

internal fun statusMessageClearAtFromPosition(item: Int): Long? {
    val currentTime = System.currentTimeMillis() / ONE_SECOND_IN_MILLIS
    return when (item) {
        StatusMessageViewModel.CLEAR_AT_POS_DONT_CLEAR -> null
        StatusMessageViewModel.CLEAR_AT_POS_FIFTEEN_MINUTES -> currentTime + FIFTEEN_MINUTES * ONE_MINUTE_IN_SECONDS
        StatusMessageViewModel.CLEAR_AT_POS_HALF_AN_HOUR -> currentTime + THIRTY_MINUTES * ONE_MINUTE_IN_SECONDS
        StatusMessageViewModel.CLEAR_AT_POS_AN_HOUR -> currentTime + ONE_MINUTE_IN_SECONDS * ONE_MINUTE_IN_SECONDS
        StatusMessageViewModel.CLEAR_AT_POS_FOUR_HOURS ->
            currentTime + FOUR_HOURS * ONE_MINUTE_IN_SECONDS * ONE_MINUTE_IN_SECONDS
        StatusMessageViewModel.CLEAR_AT_POS_TODAY -> statusMessageEndOfDay()
        StatusMessageViewModel.CLEAR_AT_POS_END_OF_WEEK -> statusMessageEndOfWeek()
        else -> null
    }
}

internal fun statusMessageEndOfDay(): Long {
    val date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, LAST_HOUR_OF_DAY)
        set(Calendar.MINUTE, LAST_MINUTE_OF_HOUR)
        set(Calendar.SECOND, LAST_SECOND_OF_MINUTE)
    }
    return date.timeInMillis / ONE_SECOND_IN_MILLIS
}

internal fun statusMessageEndOfWeek(): Long {
    val date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, LAST_HOUR_OF_DAY)
        set(Calendar.MINUTE, LAST_MINUTE_OF_HOUR)
        set(Calendar.SECOND, LAST_SECOND_OF_MINUTE)
    }
    while (date.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY) {
        date.add(Calendar.DAY_OF_YEAR, 1)
    }
    return date.timeInMillis / ONE_SECOND_IN_MILLIS
}

internal fun clearAtToUnixTime(clearAt: ClearAt?): Long? {
    clearAt ?: return null
    return when (clearAt.type) {
        "period" -> System.currentTimeMillis() / ONE_SECOND_IN_MILLIS + clearAt.time.toLong()
        "end-of" -> if (clearAt.time == "day") statusMessageEndOfDay() else null
        else -> null
    }
}

internal fun clearAtToClearAtPosition(clearAt: ClearAt?): Int {
    clearAt ?: return StatusMessageViewModel.CLEAR_AT_POS_DONT_CLEAR
    return when {
        clearAt.type == "period" -> when (clearAt.time) {
            "900" -> StatusMessageViewModel.CLEAR_AT_POS_FIFTEEN_MINUTES
            "1800" -> StatusMessageViewModel.CLEAR_AT_POS_HALF_AN_HOUR
            "3600" -> StatusMessageViewModel.CLEAR_AT_POS_AN_HOUR
            "14400" -> StatusMessageViewModel.CLEAR_AT_POS_FOUR_HOURS
            else -> StatusMessageViewModel.CLEAR_AT_POS_DONT_CLEAR
        }
        clearAt.type == "end-of" -> when (clearAt.time) {
            "day" -> StatusMessageViewModel.CLEAR_AT_POS_TODAY
            "week" -> StatusMessageViewModel.CLEAR_AT_POS_END_OF_WEEK
            else -> StatusMessageViewModel.CLEAR_AT_POS_DONT_CLEAR
        }
        else -> StatusMessageViewModel.CLEAR_AT_POS_DONT_CLEAR
    }
}
