/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.raisehand

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.generic.GenericMeta
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import io.reactivex.Observable

class RequestAssistanceRepositoryImpl(private val ncApi: NcApi, currentUserProvider: CurrentUserProviderNew) :
    RequestAssistanceRepository {

    val currentUser: User = currentUserProvider.currentUser.blockingGet()
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)!!

    var apiVersion = 1

    override fun requestAssistance(roomToken: String): Observable<RequestAssistanceModel> =
        ncApi.requestAssistance(
            credentials,
            ApiUtils.getUrlForRequestAssistance(
                apiVersion,
                currentUser.baseUrl,
                roomToken
            )
        ).map { mapToRequestAssistanceModel(it.ocs?.meta!!) }

    override fun withdrawRequestAssistance(roomToken: String): Observable<WithdrawRequestAssistanceModel> =
        ncApi.withdrawRequestAssistance(
            credentials,
            ApiUtils.getUrlForRequestAssistance(
                apiVersion,
                currentUser.baseUrl,
                roomToken
            )
        ).map { mapToWithdrawRequestAssistanceModel(it.ocs?.meta!!) }

    private fun mapToRequestAssistanceModel(response: GenericMeta): RequestAssistanceModel {
        val success = response.statusCode == HTTP_OK
        return RequestAssistanceModel(
            success
        )
    }

    private fun mapToWithdrawRequestAssistanceModel(response: GenericMeta): WithdrawRequestAssistanceModel {
        val success = response.statusCode == HTTP_OK
        return WithdrawRequestAssistanceModel(
            success
        )
    }

    companion object {
        private const val HTTP_OK: Int = 200
    }
}
