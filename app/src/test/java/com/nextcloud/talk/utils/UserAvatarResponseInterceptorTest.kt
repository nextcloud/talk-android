/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.Base64

@Suppress("TooManyFunctions")
class UserAvatarResponseInterceptorTest {

    @Test
    fun `generated avatar becomes a light PNG with matching content headers`() {
        val result = intercept()

        assertEquals(listOf(false), result.fallbackThemes)
        assertArrayEquals(LIGHT_IMAGE, result.response.body.bytes())
        assertEquals("image/png", result.response.header("Content-Type"))
        assertEquals("image/png", result.response.body.contentType().toString())
        assertEquals(LIGHT_IMAGE.size.toLong(), result.response.body.contentLength())
        assertEquals(LIGHT_IMAGE.size.toString(), result.response.header("Content-Length"))
        assertNull(result.response.header("Content-Encoding"))
    }

    @Test
    fun `dark appearance tag selects the dark fallback`() {
        val result = intercept(request = request(dark = true))

        assertEquals(listOf(true), result.fallbackThemes)
        assertArrayEquals(DARK_IMAGE, result.response.body.bytes())
    }

    @Test
    fun `dark avatar path selects the dark fallback without a tag`() {
        val result = intercept(request = request("$AVATAR_URL/dark"))

        assertEquals(listOf(true), result.fallbackThemes)
        assertArrayEquals(DARK_IMAGE, result.response.body.bytes())
    }

    @Test
    fun `explicit light appearance takes precedence over the dark path`() {
        val result = intercept(request = request("$AVATAR_URL/dark", dark = false))

        assertEquals(listOf(false), result.fallbackThemes)
        assertArrayEquals(LIGHT_IMAGE, result.response.body.bytes())
    }

    @Test
    fun `generated avatar marker tolerates surrounding whitespace`() {
        val result = intercept(marker = " 0 ")

        assertEquals(listOf(false), result.fallbackThemes)
        assertArrayEquals(LIGHT_IMAGE, result.response.body.bytes())
    }

    @Test
    fun `custom user photos are returned unchanged`() {
        assertUntouched(intercept(marker = "1"))
    }

    @Test
    fun `missing custom avatar marker preserves the server image`() {
        assertUntouched(intercept(marker = null))
    }

    @Test
    fun `unrecognized custom avatar markers preserve the server image`() {
        listOf("", "false", "00", "1, 0", "unknown").forEach { marker ->
            assertUntouched(intercept(marker = marker))
        }
    }

    @Test
    fun `ordinary photos and WebDAV files are not replaced even with a generated marker`() {
        listOf(
            "$SERVER/remote.php/dav/files/alice/photo.png",
            "$SERVER/remote.php/dav/files/alice/avatar/bob/96",
            "$SERVER/index.php/core/preview?fileId=123"
        ).forEach { url ->
            assertUntouched(intercept(request = request(url)))
        }
    }

    @Test
    fun `conversation avatars are not replaced even with a generated marker`() {
        val url = "$SERVER/ocs/v2.php/apps/spreed/api/v1/room/room-token/avatar"

        assertUntouched(intercept(request = request(url)))
    }

    @Test
    fun `failed HTTP responses are preserved`() {
        listOf(HTTP_UNAUTHORIZED, HTTP_NOT_FOUND, HTTP_SERVICE_UNAVAILABLE).forEach { code ->
            assertUntouched(intercept(code = code))
        }
    }

    @Test
    fun `avatar replacement preserves the original request URL and authentication`() {
        val originalRequest = request("$AVATAR_URL?v=7&account=alice").newBuilder()
            .header("Authorization", "Basic test-credentials")
            .header("Accept-Language", "fa")
            .build()
        val result = intercept(request = originalRequest)

        assertSame(originalRequest, result.proceededRequest)
        assertSame(originalRequest, result.response.request)
        assertEquals(originalRequest.url, result.proceededRequest.url)
        assertEquals(originalRequest.headers, result.proceededRequest.headers)
        assertEquals(1, result.requestCount)
    }

    @Test
    fun `policy recognizes subdirectory installations encoded user names and federated proxies`() {
        listOf(
            AVATAR_URL,
            "$SERVER/nextcloud/index.php/avatar/alice/96/dark",
            "$SERVER/nextcloud/avatar/Alice%20Smith/128",
            "$SERVER/index.php/avatar/alice%2Fteam/96",
            "$SERVER/index.php/avatar/%D8%B9%D9%84%DB%8C/96",
            "$SERVER/ocs/v2.php/apps/spreed/api/v1/proxy/alice%40remote.example/user-avatar/96",
            "$SERVER/nextcloud/ocs/v1.php/apps/spreed/api/v2/proxy/alice/user-avatar/128"
        ).forEach { url ->
            assertTrue(url, UserAvatarPolicy.appliesTo(url.toHttpUrl()))
            val result = intercept(request = request(url))
            assertEquals(url, 1, result.fallbackThemes.size)
        }
    }

    @Test
    fun `policy rejects unrelated and incomplete avatar paths`() {
        listOf(
            "$SERVER/avatar/alice",
            "$SERVER/avatar/alice/original",
            "$SERVER/avatar/alice/-1",
            "$SERVER/avatar/alice/96/photo.png",
            "$SERVER/files/avatar.png",
            "$SERVER/ocs/v2.php/apps/spreed/api/v1/proxy/alice/room-avatar/96"
        ).forEach { url ->
            assertFalse(url, UserAvatarPolicy.appliesTo(url.toHttpUrl()))
        }
    }

    @Test
    fun `light and dark cache entries cannot reuse each other`() {
        assertNotEquals(UserAvatarPolicy.cacheKey(AVATAR_URL, false), UserAvatarPolicy.cacheKey(AVATAR_URL, true))
    }

    @Test
    fun `cache keys retain the original account server and avatar version identity`() {
        val originalKeys = listOf(
            "$AVATAR_URL?v=1",
            "$AVATAR_URL?v=2",
            "$SERVER/avatar/bob/96?v=1",
            "https://other.example.com/avatar/alice/96?v=1"
        )
        val updatedKeys = originalKeys.map { UserAvatarPolicy.cacheKey(it, false) }

        assertEquals(originalKeys.size, updatedKeys.toSet().size)
        originalKeys.zip(updatedKeys).forEach { (original, updated) ->
            assertTrue(updated.startsWith("$original#"))
        }
    }

    @Test
    fun `cache policy version prevents reuse of original and older fallback entries`() {
        val key = UserAvatarPolicy.cacheKey(AVATAR_URL, false)

        assertEquals("$AVATAR_URL#talk-person-avatar-v2-light", key)
        assertNotEquals(AVATAR_URL, key)
        assertNotEquals("$AVATAR_URL#talk-person-avatar-v1-light", key)
    }

    private fun assertUntouched(result: Result) {
        assertSame(result.originalResponse, result.response)
        assertArrayEquals(SERVER_IMAGE, result.response.body.bytes())
        assertEquals("image/jpeg", result.response.header("Content-Type"))
        assertEquals("gzip", result.response.header("Content-Encoding"))
        assertTrue(result.fallbackThemes.isEmpty())
        assertEquals(1, result.requestCount)
    }

    private fun request(url: String = AVATAR_URL, dark: Boolean? = null): Request =
        Request.Builder()
            .url(url)
            .apply { dark?.let { tag(UserAvatarAppearance::class.java, UserAvatarAppearance(it)) } }
            .build()

    private fun intercept(request: Request = request(), marker: String? = "0", code: Int = HTTP_OK): Result {
        val originalResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("")
            .header("Content-Type", "image/jpeg")
            .header("Content-Length", SERVER_IMAGE.size.toString())
            .header("Content-Encoding", "gzip")
            .apply { marker?.let { header("x-nc-iscustomavatar", it) } }
            .body(SERVER_IMAGE.toResponseBody("image/jpeg".toMediaType()))
            .build()
        val fallbackThemes = mutableListOf<Boolean>()
        var proceededRequest = request
        var requestCount = 0
        val chain = mock<Interceptor.Chain> {
            on { request() } doReturn request
            on { proceed(any()) } doAnswer { invocation ->
                proceededRequest = invocation.getArgument(0)
                requestCount++
                originalResponse
            }
        }
        val response = UserAvatarResponseInterceptor { dark ->
            fallbackThemes.add(dark)
            if (dark) DARK_IMAGE else LIGHT_IMAGE
        }.intercept(chain)
        return Result(originalResponse, response, fallbackThemes, proceededRequest, requestCount)
    }

    private data class Result(
        val originalResponse: Response,
        val response: Response,
        val fallbackThemes: List<Boolean>,
        val proceededRequest: Request,
        val requestCount: Int
    )

    companion object {
        private const val SERVER = "https://cloud.example.com"
        private const val AVATAR_URL = "$SERVER/index.php/avatar/alice/96"
        private const val HTTP_OK = 200
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_NOT_FOUND = 404
        private const val HTTP_SERVICE_UNAVAILABLE = 503
        private val SERVER_IMAGE = "server-avatar".toByteArray()
        private val LIGHT_IMAGE = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jF1sAAAAASUVORK5CYII="
        )
        private val DARK_IMAGE = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
        )
    }
}
