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

package com.nextcloud.talk.newarch.domain.usecases.base

import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

abstract class UseCase<Type, in Params>(private val apiErrorHandler: ApiErrorHandler?) where Type : Any {

    abstract suspend fun run(params: Params? = null): Type

    fun invoke(
            scope: CoroutineScope,
            params: Params?,
            onResult: (UseCaseResponse<Type>)
    ) {
        val backgroundJob = scope.async { run(params) }
        scope.launch {
            try {
                backgroundJob.await()
                        .let {
                            onResult.onSuccess(it)
                        }
            } catch (e: Exception) {

                onResult.onError(apiErrorHandler?.traceErrorException(e))
            }
        }
    }
}