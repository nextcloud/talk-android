/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload

import at.bitfire.dav4jvm.exception.HttpException as DavHttpException
import com.nextcloud.talk.upload.UploadRetryPolicy.Decision
import com.nextcloud.talk.upload.UploadRetryPolicy.FailureKind
import android.app.Application
import android.system.ErrnoException
import android.system.OsConstants
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException as RetrofitHttpException
import retrofit2.Response
import java.io.FileNotFoundException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

@Suppress("TooManyFunctions")
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [33])
class UploadRetryPolicyTest {

    @Test
    fun `connection problems are network errors`() {
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classify(SocketTimeoutException()))
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classify(UnknownHostException()))
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classify(IOException("Canceled")))
    }

    @Test
    fun `http errors that may stay are server errors`() {
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(DavHttpException(403, "no")))
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(DavHttpException(400, "bad")))
        listOf(400, 401, 403, 404, 409, 412, 501, 505, 507).forEach {
            assertEquals("code $it", FailureKind.SERVER, UploadRetryPolicy.classify(DavHttpException(it, "x")))
            assertEquals("retrofit $it", FailureKind.SERVER, UploadRetryPolicy.classify(retrofit(it)))
        }
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(retrofit(404)))
    }

    @Test
    fun `a server that is unavailable is waited for, but not for ever`() {
        listOf(500, 502, 503, 504, 408, 429).forEach {
            assertEquals("code $it", FailureKind.UNAVAILABLE, UploadRetryPolicy.classify(DavHttpException(it, "x")))
        }
        listOf(500, 502, 503, 504, 408, 429).forEach {
            assertEquals("retrofit $it", FailureKind.UNAVAILABLE, UploadRetryPolicy.classify(retrofit(it)))
        }
    }

    @Test
    fun `http error wrapped in an IOException is still a server error`() {
        val wrapped = IOException("failed to create folder", DavHttpException(403, "no"))
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(wrapped))
    }

    @Test
    fun `an IOException wrapped by RxJava is a network error`() {
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classify(RuntimeException(IOException("reset"))))
        assertEquals(
            FailureKind.NETWORK,
            UploadRetryPolicy.classify(RuntimeException(RuntimeException(UnknownHostException())))
        )
    }

    @Test
    fun `an http error wrapped by RxJava is classified by its code`() {
        assertEquals(FailureKind.UNAVAILABLE, UploadRetryPolicy.classify(RuntimeException(retrofit(503))))
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(RuntimeException(retrofit(403))))
    }

    @Test
    fun `an http error wins over an IOException in the chain`() {
        val error = IOException("outer", RuntimeException(DavHttpException(403, "no")))
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(error))
    }

    @Test
    fun `certificate problems count against the limit`() {
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(SSLPeerUnverifiedException("who")))
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(UnknownServiceException("cleartext")))
        val untrusted = SSLHandshakeException("bad certificate").apply {
            initCause(CertPathValidatorException("trust anchor not found"))
        }
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(untrusted))
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(RuntimeException(untrusted)))
        val expired = SSLHandshakeException("expired").apply { initCause(CertificateExpiredException()) }
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(expired))
    }

    @Test
    fun `a connection that drops inside TLS is a network error`() {
        val reset = SSLException("Read error: ssl=0x1: I/O error during system call, Connection reset by peer")
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classify(reset))
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classify(SSLException("Write error: Broken pipe")))
        assertEquals(
            FailureKind.NETWORK,
            UploadRetryPolicy.classify(SSLHandshakeException("Connection closed by peer"))
        )
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classify(RuntimeException(reset)))
    }

    @Test
    fun `a full or read only disk fails the preparation at once`() {
        val full = IOException("write failed: ENOSPC", ErrnoException("write", OsConstants.ENOSPC))
        val readOnly = IOException("open failed: EROFS", ErrnoException("open", OsConstants.EROFS))
        assertEquals(FailureKind.OTHER, UploadRetryPolicy.classifyPreparation(full))
        assertEquals(FailureKind.OTHER, UploadRetryPolicy.classifyPreparation(readOnly))
        assertEquals(FailureKind.OTHER, UploadRetryPolicy.classifyPreparation(FileNotFoundException()))
    }

    @Test
    fun `other IO errors while preparing are waited for`() {
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classifyPreparation(IOException("stream closed")))
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classifyPreparation(SocketTimeoutException()))
        val brokenPipe = IOException("write failed", ErrnoException("write", OsConstants.EPIPE))
        assertEquals(FailureKind.NETWORK, UploadRetryPolicy.classifyPreparation(brokenPipe))
    }

    @Test
    fun `local problems are neither network nor server errors`() {
        assertEquals(FailureKind.OTHER, UploadRetryPolicy.classify(FileNotFoundException()))
        assertEquals(FailureKind.OTHER, UploadRetryPolicy.classify(RuntimeException(FileNotFoundException())))
        assertEquals(FailureKind.OTHER, UploadRetryPolicy.classify(IllegalArgumentException()))
    }

    @Test
    fun `a chunk that cannot be read fails at once whatever its cause is`() {
        // ChunkFromFileRequestBody wraps every IOException of reading the chunk in a FileNotFoundException.
        val unreadable = FileNotFoundException("Exception reading source file").apply { initCause(IOException("EIO")) }
        assertEquals(FailureKind.OTHER, UploadRetryPolicy.classify(unreadable))
        assertEquals(FailureKind.OTHER, UploadRetryPolicy.classify(RuntimeException(unreadable)))
        assertEquals(FailureKind.OTHER, UploadRetryPolicy.classify(IOException("outer", unreadable)))
    }

    @Test
    fun `an http error wins over an unreadable file`() {
        val error = FileNotFoundException("x").apply { initCause(DavHttpException(403, "no")) }
        assertEquals(FailureKind.SERVER, UploadRetryPolicy.classify(error))
    }

    @Test
    fun `network errors never use up attempts`() {
        assertEquals(Decision.RETRY, UploadRetryPolicy.decide(FailureKind.NETWORK, 0, 0L))
        assertEquals(
            Decision.RETRY,
            UploadRetryPolicy.decide(FailureKind.NETWORK, 0, UploadRetryPolicy.MAX_UNAVAILABLE_MILLIS * 2)
        )
    }

    @Test
    fun `an unavailable server is waited for up to 24 hours`() {
        val limit = UploadRetryPolicy.MAX_UNAVAILABLE_MILLIS
        assertEquals(24L * 60 * 60 * 1000, limit)
        assertEquals(Decision.RETRY, UploadRetryPolicy.decide(FailureKind.UNAVAILABLE, 0, 0L))
        assertEquals(Decision.RETRY, UploadRetryPolicy.decide(FailureKind.UNAVAILABLE, 0, limit - 1))
        assertEquals(Decision.FAIL, UploadRetryPolicy.decide(FailureKind.UNAVAILABLE, 0, limit))
        assertEquals(Decision.FAIL, UploadRetryPolicy.decide(FailureKind.UNAVAILABLE, 0, limit + 1))
    }

    @Test
    fun `server errors are retried until the limit`() {
        for (count in 1 until UploadRetryPolicy.MAX_SERVER_ERRORS) {
            assertEquals(Decision.RETRY, UploadRetryPolicy.decide(FailureKind.SERVER, count, 0L))
        }
        assertEquals(
            Decision.FAIL,
            UploadRetryPolicy.decide(FailureKind.SERVER, UploadRetryPolicy.MAX_SERVER_ERRORS, 0L)
        )
    }

    @Test
    fun `other errors fail at once`() {
        assertEquals(Decision.FAIL, UploadRetryPolicy.decide(FailureKind.OTHER, 0, 0L))
    }

    private fun retrofit(code: Int) = RetrofitHttpException(Response.error<Any>(code, "".toResponseBody()))
}
