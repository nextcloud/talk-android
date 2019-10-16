/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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
 *
 * Inspired by https://github.com/ZahraHeydari/Android-Clean-Arch-Coroutines-Koin/blob
 * /cfed12ecbfa425de53de505e76b3ec06c3bbdaca/app/src/main/java/com/android/post/data/source/remote/ApiErrorHandle.kt
 */

package com.nextcloud.talk.newarch.data.source.remote

import com.nextcloud.talk.newarch.data.model.ErrorModel
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * This class trace exceptions(api call or parse data or connection errors) &
 * depending on what exception returns a [ErrorModel]
 *
 * */
class ApiErrorHandler {

  fun traceErrorException(throwable: Throwable?): ErrorModel {
    val errorModel: ErrorModel? = when (throwable) {

      // if throwable is an instance of HttpException
      // then attempt to parse error data from response body
      is HttpException -> {
        // handle UNAUTHORIZED situation (when token expired)
        if (throwable.code() == 401) {
          ErrorModel(throwable.message(), throwable.code(), ErrorModel.ErrorStatus.UNAUTHORIZED)
        } else {
          getHttpError(throwable.response()?.errorBody())
        }
      }

      // handle api call timeout error
      is SocketTimeoutException -> {
        ErrorModel(throwable.message, ErrorModel.ErrorStatus.TIMEOUT)
      }

      // handle connection error
      is IOException -> {
        ErrorModel(throwable.message, ErrorModel.ErrorStatus.NO_CONNECTION)
      }
      else -> null
    }
    return errorModel ?: ErrorModel("No Defined Error!", 0, ErrorModel.ErrorStatus.BAD_RESPONSE)
  }

  /**
   * attempts to parse http response body and get error data from it
   *
   * @param body retrofit response body
   * @return returns an instance of [ErrorModel] with parsed data or NOT_DEFINED status
   */
  private fun getHttpError(body: ResponseBody?): ErrorModel {
    return try {
      // use response body to get error detail
      ErrorModel(body.toString(), 400, ErrorModel.ErrorStatus.BAD_RESPONSE)
    } catch (e: Throwable) {
      e.printStackTrace()
      ErrorModel(message = e.message, errorStatus = ErrorModel.ErrorStatus.NOT_DEFINED)
    }

  }
}