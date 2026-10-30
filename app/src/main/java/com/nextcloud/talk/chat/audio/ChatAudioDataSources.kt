/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.nextcloud.talk.users.UserManager
import com.nextcloud.talk.utils.ApiUtils
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data sources for chat audio. Files are streamed through the app's HTTP client, so proxy settings and user
 * accepted certificates apply, and are kept in a size limited disk cache that is shared by the player and the
 * waveform extraction. There must be only one instance per process, as a cache directory can only be used once.
 */
@OptIn(UnstableApi::class)
@Singleton
class ChatAudioDataSources @Inject constructor(
    context: Context,
    okHttpClient: OkHttpClient,
    private val userManager: UserManager
) {
    private val cache = SimpleCache(
        File(context.cacheDir, CACHE_DIRECTORY),
        LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE_BYTES),
        StandaloneDatabaseProvider(context)
    )

    val cacheDataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(
            DefaultDataSource.Factory(
                context,
                ResolvingDataSource.Factory(OkHttpDataSource.Factory(okHttpClient)) { dataSpec ->
                    authenticate(dataSpec)
                }
            )
        )
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private fun authenticate(dataSpec: DataSpec): DataSpec {
        val credentials = credentialsFor(dataSpec.uri.toString())
        return if (credentials == null) {
            dataSpec
        } else {
            dataSpec.withAdditionalHeaders(mapOf(AUTHORIZATION_HEADER to credentials))
        }
    }

    private fun credentialsFor(url: String): String? =
        runBlocking { userManager.getUsers() }
            .firstOrNull { user -> ChatAudioCredentials.belongsToUser(url, user.baseUrl, user.userId) }
            ?.let { user -> ApiUtils.getCredentials(user.username, user.token) }

    companion object {
        private const val CACHE_DIRECTORY = "chat_audio"
        private const val MAX_CACHE_SIZE_BYTES = 200L * 1024 * 1024
        private const val AUTHORIZATION_HEADER = "Authorization"
    }
}
