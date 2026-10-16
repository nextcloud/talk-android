/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import coil.annotation.ExperimentalCoilApi
import coil.intercept.Interceptor
import coil.memory.MemoryCache
import coil.request.ErrorResult
import coil.request.ImageRequest
import coil.request.ImageResult
import coil.size.Size
import com.nextcloud.talk.utils.AccountScopedImageInterceptor.Companion.accountScope
import com.nextcloud.talk.utils.AccountScopedImageInterceptor.Companion.isAccountDependentUrl
import com.nextcloud.talk.utils.AccountScopedImageInterceptor.Companion.scopedCacheKey
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AccountScopedImageInterceptorTest {

    @Test
    fun `conversation avatar and file preview by path urls are recognized as account dependent`() {
        val baseUrl = "https://cloud.example.com"
        assertTrue(isAccountDependentUrl(PREVIEW_BY_PATH_URL))
        assertTrue(isAccountDependentUrl(ApiUtils.getUrlForConversationAvatar(1, baseUrl, TOKEN)))
        assertTrue(
            isAccountDependentUrl(ApiUtils.getUrlForConversationAvatarWithVersion(1, baseUrl, TOKEN, true, null))
        )
        assertTrue(
            isAccountDependentUrl(ApiUtils.getUrlForConversationAvatarWithVersion(1, baseUrl, TOKEN, false, "5"))
        )
    }

    @Test
    fun `other urls are not recognized as account dependent`() {
        val baseUrl = "https://cloud.example.com"
        assertFalse(isAccountDependentUrl(ApiUtils.getUrlForFilePreviewWithFileId(baseUrl, "42", 64)))
        assertFalse(isAccountDependentUrl(ApiUtils.getUrlForAvatar(baseUrl, "alice", true)))
        assertFalse(isAccountDependentUrl(ApiUtils.getUrlForConversationEmojiAvatar(1, baseUrl, TOKEN)))
        assertFalse(isAccountDependentUrl(ApiUtils.getUrlForRoom(1, baseUrl, TOKEN)))
    }

    @Test
    fun `account scope differs between accounts and is stable for one account`() {
        assertEquals(accountScope(ALICE), accountScope(ALICE))
        assertNotEquals(accountScope(ALICE), accountScope(BOB))
    }

    @Test
    fun `account scope does not contain the credentials`() {
        val scope = accountScope(ALICE)
        assertFalse(scope.contains(ALICE))
        assertFalse(ALICE.contains(scope))
    }

    @Test
    fun `disk cache keys of conversation avatars differ between accounts and keep a custom key`() =
        runTest {
            val aliceRequest = intercept(authorizedRequest(ALICE, diskCacheKey = "$AVATAR_URL#v2"))
            val bobRequest = intercept(authorizedRequest(BOB, diskCacheKey = "$AVATAR_URL#v2"))

            assertNotEquals(aliceRequest.diskCacheKey, bobRequest.diskCacheKey)
            assertTrue(aliceRequest.diskCacheKey!!.endsWith(":$AVATAR_URL#v2"))
            assertTrue(bobRequest.diskCacheKey!!.endsWith(":$AVATAR_URL#v2"))
        }

    @Test
    fun `disk cache key of conversation avatars falls back to the scoped url`() =
        runTest {
            val request = intercept(authorizedRequest(ALICE))

            assertEquals(scopedCacheKey(accountScope(ALICE), AVATAR_URL), request.diskCacheKey)
        }

    @Test
    fun `cache keys of file previews by path differ between accounts`() =
        runTest {
            val aliceRequest = intercept(authorizedRequest(ALICE, PREVIEW_BY_PATH_URL))
            val bobRequest = intercept(authorizedRequest(BOB, PREVIEW_BY_PATH_URL))

            assertNotEquals(aliceRequest.diskCacheKey, bobRequest.diskCacheKey)
            assertNotEquals(aliceRequest.parameters.memoryCacheKeys(), bobRequest.parameters.memoryCacheKeys())
        }

    @Test
    fun `memory cache keys of conversation avatars differ between accounts`() =
        runTest {
            val aliceRequest = intercept(authorizedRequest(ALICE))
            val bobRequest = intercept(authorizedRequest(BOB))

            assertNotEquals(
                aliceRequest.parameters.memoryCacheKeys(),
                bobRequest.parameters.memoryCacheKeys()
            )
        }

    @Test
    fun `explicit memory cache keys of conversation avatars are scoped and keep their extras`() =
        runTest {
            val extras = mapOf("size" to "64")
            val request = intercept(
                authorizedRequest(ALICE).newBuilder().memoryCacheKey(MemoryCache.Key(AVATAR_URL, extras)).build()
            )

            assertNotEquals(AVATAR_URL, request.memoryCacheKey!!.key)
            assertTrue(request.memoryCacheKey!!.key.endsWith(":$AVATAR_URL"))
            assertEquals(extras, request.memoryCacheKey!!.extras)
        }

    @Test
    fun `other requests are left unchanged`() =
        runTest {
            val userAvatarUrl = ApiUtils.getUrlForAvatar("https://cloud.example.com", "alice", true)
            val request = authorizedRequest(ALICE, userAvatarUrl)

            val result = intercept(request)

            assertNull(result.diskCacheKey)
            assertTrue(result.parameters.isEmpty())
        }

    @Test
    fun `conversation avatar requests without credentials are left unchanged`() =
        runTest {
            val result = intercept(imageRequest(AVATAR_URL))

            assertNull(result.diskCacheKey)
            assertTrue(result.parameters.isEmpty())
        }

    private fun imageRequest(url: String): ImageRequest =
        ImageRequest.Builder(RuntimeEnvironment.getApplication()).data(url).build()

    private fun authorizedRequest(
        authorization: String,
        url: String = AVATAR_URL,
        diskCacheKey: String? = null
    ): ImageRequest =
        imageRequest(url).newBuilder()
            .addHeader("Authorization", authorization)
            .apply { diskCacheKey?.let { diskCacheKey(it) } }
            .build()

    private suspend fun intercept(request: ImageRequest): ImageRequest {
        val chain = RecordingChain(request)
        AccountScopedImageInterceptor().intercept(chain)
        return chain.proceededRequest!!
    }

    private class RecordingChain(override val request: ImageRequest) : Interceptor.Chain {
        var proceededRequest: ImageRequest? = null

        override val size: Size = Size.ORIGINAL

        @ExperimentalCoilApi
        override fun withRequest(request: ImageRequest): Interceptor.Chain = RecordingChain(request)

        override fun withSize(size: Size): Interceptor.Chain = this

        override suspend fun proceed(request: ImageRequest): ImageResult {
            proceededRequest = request
            return ErrorResult(null, request, IllegalStateException("not loaded in tests"))
        }
    }

    companion object {
        private const val TOKEN = "abc123"
        private const val ALICE = "Basic YWxpY2U6dG9rZW4tYQ=="
        private const val BOB = "Basic Ym9iOnRva2VuLWI="
        private val AVATAR_URL = ApiUtils.getUrlForConversationAvatar(1, "https://cloud.example.com", TOKEN)
        private val PREVIEW_BY_PATH_URL =
            ApiUtils.getUrlForFilePreviewWithRemotePath("https://cloud.example.com", "/Photos/a.jpg", 64)
    }
}
