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

package com.nextcloud.talk.newarch.utils

import com.nextcloud.talk.newarch.data.repository.online.NextcloudTalkRepositoryImpl
import com.nextcloud.talk.newarch.data.source.remote.ApiService
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.koin.core.KoinComponent
import retrofit2.Retrofit
import java.net.CookieManager

class NextcloudRepositoryWithNoCookies(
        private val okHttpClient: OkHttpClient,
        private val retrofit: Retrofit
) : KoinComponent {
    fun getRepository(): NextcloudTalkRepository {
        return NextcloudTalkRepositoryImpl(retrofit.newBuilder().client(
                okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build())
                .build().create(ApiService::class.java))
    }

}