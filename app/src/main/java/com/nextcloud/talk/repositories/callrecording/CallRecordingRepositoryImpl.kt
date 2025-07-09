/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.callrecording

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.domain.StartCallRecordingModel
import com.nextcloud.talk.models.domain.StopCallRecordingModel
import com.nextcloud.talk.models.json.generic.GenericMeta
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable

class CallRecordingRepositoryImpl(private val ncApi: NcApi, currentUserProvider: CurrentUserProviderNew) :
    CallRecordingRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!

    var apiVersion = 1

    override fun startRecording(roomToken: String): Observable<StartCallRecordingModel> =
        ncApi.startRecording(
            credentials,
            ApiUtils.getUrlForRecording(
                apiVersion,
                currentUser.baseUrl!!,
                roomToken
            ),
            1
        ).map { mapToStartCallRecordingModel(it.ocs?.meta!!) }

    override fun stopRecording(roomToken: String): Observable<StopCallRecordingModel> =
        ncApi.stopRecording(
            credentials,
            ApiUtils.getUrlForRecording(
                apiVersion,
                currentUser.baseUrl!!,
                roomToken
            )
        ).map { mapToStopCallRecordingModel(it.ocs?.meta!!) }

    private fun mapToStartCallRecordingModel(response: GenericMeta): StartCallRecordingModel {
        val success = response.statusCode == HTTP_OK
        return StartCallRecordingModel(
            success
        )
    }

    private fun mapToStopCallRecordingModel(response: GenericMeta): StopCallRecordingModel {
        val success = response.statusCode == HTTP_OK
        return StopCallRecordingModel(
            success
        )
    }

    companion object {
        private const val HTTP_OK: Int = 200
    }
}
