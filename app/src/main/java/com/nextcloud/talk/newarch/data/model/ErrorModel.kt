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
 * Inspired by: https://github.com/ZahraHeydari/Android-Clean-Arch-Coroutines-Koin/blob/c63e62bf8fb9c099a983fcc7d1b5616a7318aa3f/app/src/main/java/com/android/post/data/model/ErrorModel.kt
 */

package com.nextcloud.talk.newarch.data.model

/**
 * This class designed to show different types of errors through error status & message
 *
 * */

private const val NO_CONNECTION_ERROR_MESSAGE = "No connection"
private const val BAD_RESPONSE_ERROR_MESSAGE = "Bad response"
private const val TIME_OUT_ERROR_MESSAGE = "Time out"
private const val EMPTY_RESPONSE_ERROR_MESSAGE = "Empty response"
private const val NOT_DEFINED_ERROR_MESSAGE = "Not defined"
private const val UNAUTHORIZED_ERROR_MESSAGE = "Unauthorized"

data class ErrorModel(
  val message: String?,
  val code: Int?,
  var errorStatus: ErrorStatus
) {
  constructor(errorStatus: ErrorStatus) : this(null, null, errorStatus)

  constructor(
    message: String?,
    errorStatus: ErrorStatus
  ) : this(message, null, errorStatus)

  fun getErrorMessage(): String {
    return when (errorStatus) {
      ErrorStatus.NO_CONNECTION -> NO_CONNECTION_ERROR_MESSAGE
      ErrorStatus.BAD_RESPONSE -> BAD_RESPONSE_ERROR_MESSAGE
      ErrorStatus.TIMEOUT -> TIME_OUT_ERROR_MESSAGE
      ErrorStatus.EMPTY_RESPONSE -> EMPTY_RESPONSE_ERROR_MESSAGE
      ErrorStatus.NOT_DEFINED -> NOT_DEFINED_ERROR_MESSAGE
      ErrorStatus.UNAUTHORIZED -> UNAUTHORIZED_ERROR_MESSAGE
    }
  }

  /**
   * various error status to know what happened if something goes wrong with a repository call
   */
  enum class ErrorStatus {
    /**
     * error in connecting to repository (Server or Database)
     */
    NO_CONNECTION,
    /**
     * error in getting value (Json Error, Server Error, etc)
     */
    BAD_RESPONSE,
    /**
     * Time out  error
     */
    TIMEOUT,
    /**
     * no data available in repository
     */
    EMPTY_RESPONSE,
    /**
     * an unexpected error
     */
    NOT_DEFINED,
    /**
     * bad credentials
     */
    UNAUTHORIZED
  }
}