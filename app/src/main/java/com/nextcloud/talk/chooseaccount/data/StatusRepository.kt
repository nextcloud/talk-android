/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chooseaccount.data

import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.status.StatusOverall
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus

interface StatusRepository {
    suspend fun setStatus(credentials: String, url: String): StatusOverall
    suspend fun setStatusType(credentials: String, url: String, statusType: String): GenericOverall
    suspend fun getPredefinedStatuses(credentials: String, url: String): List<PredefinedStatus>
    suspend fun getBackupStatus(credentials: String, url: String): StatusOverall
    suspend fun clearStatusMessage(credentials: String, url: String): GenericOverall
    suspend fun setPredefinedStatusMessage(
        credentials: String,
        url: String,
        messageId: String,
        clearAt: Long?
    ): GenericOverall
    suspend fun setCustomStatusMessage(
        credentials: String,
        url: String,
        statusIcon: String?,
        message: String,
        clearAt: Long?
    ): GenericOverall
    suspend fun revertStatus(credentials: String, url: String): GenericOverall
}
