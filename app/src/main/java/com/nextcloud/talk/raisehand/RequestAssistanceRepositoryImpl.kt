/*
 * Nextcloud Talk application
 *
 * @author Marcel Hibbe
 * Copyright (C) 2023 Marcel Hibbe <dev@mhibbe.de>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
    val credentials: String = ApiUtils.getCredentials(currentUser.username, currentUser.token)

    var apiVersion = 1

    override fun requestAssistance(roomToken: String): Observable<RequestAssistanceModel> {
        return ncApi.requestAssistance(
            credentials,
            ApiUtils.getUrlForRequestAssistance(
                apiVersion,
                currentUser.baseUrl,
                roomToken
            )
        ).map { mapToRequestAssistanceModel(it.ocs?.meta!!) }
    }

    override fun withdrawRequestAssistance(roomToken: String): Observable<WithdrawRequestAssistanceModel> {
        return ncApi.withdrawRequestAssistance(
            credentials,
            ApiUtils.getUrlForRequestAssistance(
                apiVersion,
                currentUser.baseUrl,
                roomToken
            )
        ).map { mapToWithdrawRequestAssistanceModel(it.ocs?.meta!!) }
    }

    private fun mapToRequestAssistanceModel(
        response: GenericMeta
    ): RequestAssistanceModel {
        val success = response.statusCode == HTTP_OK
        return RequestAssistanceModel(
            success
        )
    }

    private fun mapToWithdrawRequestAssistanceModel(
        response: GenericMeta
    ): WithdrawRequestAssistanceModel {
        val success = response.statusCode == HTTP_OK
        return WithdrawRequestAssistanceModel(
            success
        )
    }

    companion object {
        private const val HTTP_OK: Int = 200
    }
}
