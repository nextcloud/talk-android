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

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import com.nextcloud.talk.newarch.data.repository.online.NextcloudTalkRepositoryImpl
import com.nextcloud.talk.newarch.data.source.remote.ApiService
import com.nextcloud.talk.newarch.domain.repository.online.NextcloudTalkRepository
import com.nextcloud.talk.newarch.local.models.User
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import org.koin.core.KoinComponent
import retrofit2.Retrofit
import java.net.CookieManager

class NetworkComponents(
        private val okHttpClient: OkHttpClient,
        private val retrofit: Retrofit,
        private val androidApplication: Application
) : KoinComponent {
    val usersSingleOperationRepositoryMap: MutableMap<Long, NextcloudTalkRepository> = mutableMapOf()
    val usersMultipleOperationsRepositoryMap: MutableMap<Long, NextcloudTalkRepository> = mutableMapOf()
    val usersSingleOperationOkHttpMap: MutableMap<Long, OkHttpClient> = mutableMapOf()
    val usersMultipleOperationOkHttpMap: MutableMap<Long, OkHttpClient> = mutableMapOf()
    val usersImageLoaderMap: MutableMap<Long, ImageLoader> = mutableMapOf()

    fun getRepository(singleOperation: Boolean, user: User): NextcloudTalkRepository {
        val mappedNextcloudTalkRepository = if (singleOperation) {
            usersSingleOperationRepositoryMap[user.id]
        } else {
            usersMultipleOperationsRepositoryMap[user.id]
        }

        if (mappedNextcloudTalkRepository != null && user.id != -1L) {
            return mappedNextcloudTalkRepository
        }

        return NextcloudTalkRepositoryImpl(retrofit.newBuilder().client(getOkHttpClient(singleOperation, user))
                .build().create(ApiService::class.java))
    }

    fun getOkHttpClient(singleOperation: Boolean, user: User): OkHttpClient {
        val mappedOkHttpClient = if (singleOperation) {
            usersSingleOperationOkHttpMap[user.id]
        } else {
            usersMultipleOperationOkHttpMap[user.id]
        }

        if (mappedOkHttpClient != null && user.id != -1L) {
            return mappedOkHttpClient
        }

        val okHttpClientBuilder = okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(CookieManager()))
        val dispatcher = okHttpClient.dispatcher()
        if (singleOperation) {
            dispatcher.maxRequests = 1
        } else {
            dispatcher.maxRequests = 100
        }

        okHttpClientBuilder.dispatcher(dispatcher)

        return okHttpClientBuilder.build()
    }

    fun getImageLoader(user: User): ImageLoader {
        var mappedImageLoader = usersImageLoaderMap[user.id]

        if (mappedImageLoader == null || user.id == -1L) {
            mappedImageLoader = ImageLoader(androidApplication) {
                availableMemoryPercentage(0.5)
                bitmapPoolPercentage(0.5)
                crossfade(false)
                okHttpClient(getOkHttpClient(false, user))
                componentRegistry {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder())
                    } else {
                        add(GifDecoder())
                    }
                    add(SvgDecoder(androidApplication))
                }
            }

            usersImageLoaderMap[user.id!!] = mappedImageLoader
        }

        return mappedImageLoader

    }
}