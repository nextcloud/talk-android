/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chooseaccount.data

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.status.StatusOverall
import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus
import javax.inject.Inject

class StatusRepositoryImplementation @Inject constructor(private val ncApiCoroutines: NcApiCoroutines) :
    StatusRepository {

    override suspend fun setStatus(credentials: String, url: String): StatusOverall =
        ncApiCoroutines.status(credentials, url)

    override suspend fun setStatusType(credentials: String, url: String, statusType: String): GenericOverall =
        ncApiCoroutines.setStatusType(credentials, url, statusType)

    override suspend fun getPredefinedStatuses(credentials: String, url: String): List<PredefinedStatus> =
        ncApiCoroutines.getPredefinedStatuses(credentials, url).ocs?.data.orEmpty()

    override suspend fun getBackupStatus(credentials: String, url: String): StatusOverall =
        ncApiCoroutines.backupStatus(credentials, url)

    override suspend fun clearStatusMessage(credentials: String, url: String): GenericOverall =
        ncApiCoroutines.statusDeleteMessage(credentials, url)

    override suspend fun setPredefinedStatusMessage(
        credentials: String,
        url: String,
        messageId: String,
        clearAt: Long?
    ): GenericOverall = ncApiCoroutines.setPredefinedStatusMessage(credentials, url, messageId, clearAt)

    override suspend fun setCustomStatusMessage(
        credentials: String,
        url: String,
        statusIcon: String?,
        message: String,
        clearAt: Long?
    ): GenericOverall = ncApiCoroutines.setCustomStatusMessage(credentials, url, statusIcon, message, clearAt)

    override suspend fun revertStatus(credentials: String, url: String): GenericOverall =
        ncApiCoroutines.revertStatus(credentials, url)
}
