/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.raisehand

import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.models.json.generic.GenericMeta
import io.reactivex.Observable

class RequestAssistanceRepositoryImpl(private val ncApi: NcApi) : RequestAssistanceRepository {

    override fun requestAssistance(
        credentials: String,
        url: String,
        roomToken: String
    ): Observable<RequestAssistanceModel> =
        ncApi.requestAssistance(
            credentials,
            url
        ).map { mapToRequestAssistanceModel(it.ocs?.meta!!) }

    override fun withdrawRequestAssistance(
        credentials: String,
        url: String,
        roomToken: String
    ): Observable<WithdrawRequestAssistanceModel> =
        ncApi.withdrawRequestAssistance(
            credentials,
            url
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
