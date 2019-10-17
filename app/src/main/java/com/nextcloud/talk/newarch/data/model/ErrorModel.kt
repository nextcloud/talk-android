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

import com.nextcloud.talk.R
import com.nextcloud.talk.application.NextcloudTalkApplication

/**
 * This class designed to show different types of errors through error status & message
 *
 * */

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
      ErrorStatus.NO_CONNECTION -> NextcloudTalkApplication.sharedApplication?.resources!!.getString(
          R.string.nc_no_connection_error
      )
      ErrorStatus.BAD_RESPONSE -> NextcloudTalkApplication.sharedApplication?.resources!!.getString(
          R.string.nc_bad_response_error
      )
      ErrorStatus.TIMEOUT -> NextcloudTalkApplication.sharedApplication?.resources!!.getString(
          R.string.nc_timeout_error
      )
      ErrorStatus.EMPTY_RESPONSE -> NextcloudTalkApplication.sharedApplication?.resources!!.getString(
          R.string.nc_empty_response_error
      )
      ErrorStatus.NOT_DEFINED -> NextcloudTalkApplication.sharedApplication?.resources!!.getString(
          R.string.nc_not_defined_error
      )
      ErrorStatus.UNAUTHORIZED -> NextcloudTalkApplication.sharedApplication?.resources!!
          .getString(R.string.nc_unauthorized_error)
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