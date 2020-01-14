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

package com.nextcloud.talk.newarch.domain.usecases

import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.domain.usecases.base.UseCase
import org.koin.core.parameter.DefinitionParameters

class UnregisterPushWithProxyUseCase constructor(
        private val nextcloudTalkRepository: NextcloudTalkRepository,
        apiErrorHandler: ApiErrorHandler?
) : UseCase<Any, Any?>(apiErrorHandler) {
    override suspend fun run(params: Any?): Any {
        val definitionParameters = params as DefinitionParameters
        return nextcloudTalkRepository.unregisterPushWithProxyForUser(definitionParameters[0], definitionParameters[1])
    }
}
