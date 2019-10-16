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
 */

package com.nextcloud.talk.newarch.utils

import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.preferences.AppPreferences
import com.nextcloud.talk.utils.singletons.AvatarStatusCodeHolder
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Proxy.NO_PROXY
import java.net.Proxy.Type
import java.net.Proxy.Type.SOCKS

class NetworkUtils {
  class HeadersInterceptor : Interceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
      val original = chain.request()
      val request = original.newBuilder()
          .header("User-Agent", ApiUtils.getUserAgent())
          .header("Accept", "application/json")
          .header("OCS-APIRequest", "true")
          .method(original.method, original.body)
          .build()

      val response = chain.proceed(request)

      if (request.url.encodedPath.contains("/avatar/")) {
        AvatarStatusCodeHolder.getInstance()
            .statusCode = response.code
      }

      return response
    }
  }

  class MagicAuthenticator(
    private val credentials: String,
    private val authenticatorType: String
  ) : Authenticator {

    override fun authenticate(
      route: Route?,
      response: Response
    ): Request? {
      if (response.request.header(authenticatorType) != null) {
        return null
      }

      var countedResponse: Response? = response

      var attemptsCount = 0


      while ({ countedResponse = countedResponse?.priorResponse; countedResponse }() != null) {
        attemptsCount++
        if (attemptsCount == 3) {
          return null
        }
      }
      return response.request.newBuilder()
          .header(authenticatorType, credentials)
          .build()
    }
  }

  class GetProxyRunnable internal constructor(private val appPreferences: AppPreferences) :
      Runnable {
    @Volatile internal var proxyValue: Proxy = NO_PROXY
      private set

    override fun run() {
      if (SOCKS == Type.valueOf(appPreferences.proxyType)) {
        proxyValue = Proxy(
            Type.valueOf(appPreferences.proxyType),
            InetSocketAddress.createUnresolved(
                appPreferences.proxyHost, Integer.parseInt(
                appPreferences.proxyPort
            )
            )
        )
      } else {
        proxyValue = Proxy(
            Type.valueOf(appPreferences.proxyType),
            InetSocketAddress(
                appPreferences.proxyHost,
                Integer.parseInt(appPreferences.proxyPort)
            )
        )
      }
    }
  }
}