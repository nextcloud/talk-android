/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.api

import com.nextcloud.talk.logger.Level
import com.nextcloud.talk.logger.Logger
import com.nextcloud.talk.logger.LogsRepository
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import javax.inject.Inject

class LoggingHttpInterceptor @Inject constructor(
    private val logger: Logger,
    private val logsRepository: LogsRepository
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (logsRepository.minimumLevel > Level.DEBUG) {
            return chain.proceed(chain.request())
        }
        // A local StringBuilder is safe here because HttpLoggingInterceptor calls
        // the lambda synchronously and sequentially within this single intercept() call.
        val sb = StringBuilder()
        return HttpLoggingInterceptor { line ->
            if (sb.isNotEmpty()) sb.append('\n')
            sb.append(line)
            if (line.startsWith("<-- END HTTP") || line.startsWith("<-- HTTP FAILED")) {
                logger.d("HTTP", sb.toString())
                sb.clear()
            }
        }.apply {
            level = HttpLoggingInterceptor.Level.BODY
            redactHeader("Authorization")
            redactHeader("Proxy-Authorization")
        }.intercept(chain)
    }
}
