/*
 *
 *  * Nextcloud Talk application
 *  *
 *  * @author Mario Danic
 *  * Copyright (C) 2017-2020 Mario Danic <mario@lovelyhq.com>
 *  *
 *  * This program is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * at your option) any later version.
 *  *
 *  * This program is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */

package com.nextcloud.talk.newarch.features.account.serverentry

import android.app.Application
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.newarch.conversationsList.mvp.BaseViewModel
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.domain.usecases.GetCapabilitiesUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import org.koin.core.parameter.parametersOf

class ServerEntryViewModel constructor(
        application: Application,
        private val getCapabilitiesUseCase: GetCapabilitiesUseCase
) : BaseViewModel<ServerEntryView>(application) {
    val checkState: MutableLiveData<ServerEntryCapabilitiesCheckStateWrapper> = MutableLiveData(ServerEntryCapabilitiesCheckStateWrapper(ServerEntryCapabilitiesCheckState.WAITING_FOR_INPUT, null))

    fun fetchCapabilities(url: String) {
        checkState.postValue(ServerEntryCapabilitiesCheckStateWrapper(ServerEntryCapabilitiesCheckState.CHECKING, url))
        getCapabilitiesUseCase.invoke(viewModelScope, parametersOf(url), object : UseCaseResponse<CapabilitiesOverall> {
            override suspend fun onSuccess(result: CapabilitiesOverall) {
                val hasSupportedTalkVersion = result.ocs?.data?.capabilities?.spreedCapability?.features?.contains("no-ping") == true
                if (hasSupportedTalkVersion) {
                    checkState.postValue(ServerEntryCapabilitiesCheckStateWrapper(ServerEntryCapabilitiesCheckState.SERVER_SUPPORTED, url))
                } else {
                    checkState.postValue(ServerEntryCapabilitiesCheckStateWrapper(ServerEntryCapabilitiesCheckState.SERVER_UNSUPPORTED, url))
                }
            }

            override suspend fun onError(errorModel: ErrorModel?) {
                if (url.startsWith("https://")) {
                    fetchCapabilities(url.replace("https://", "http://"))
                } else {
                    checkState.postValue(ServerEntryCapabilitiesCheckStateWrapper(ServerEntryCapabilitiesCheckState.SERVER_UNSUPPORTED, url))
                }
            }
        })
    }
}