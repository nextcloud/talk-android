/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.upload

import android.system.ErrnoException
import android.system.OsConstants
import at.bitfire.dav4jvm.exception.HttpException as DavHttpException
import retrofit2.HttpException as RetrofitHttpException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownServiceException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Decides what an upload does after an error or after WorkManager stopped it.
 *
 * Network problems and stops by the system never use up attempts: the upload resumes once the network is back.
 * Only errors answered by the server count against [MAX_SERVER_ERRORS].
 */
object UploadRetryPolicy {

    /** Server errors one upload may hit before it is marked as failed. */
    const val MAX_SERVER_ERRORS = 4

    private const val MAX_CAUSE_DEPTH = 8
    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
    private const val HTTP_INSUFFICIENT_STORAGE = 507

    /**
     * NETWORK: no connection or a server that is temporarily unavailable, retried without a limit.
     * SERVER: an answer or a TLS problem that may not go away, retried up to [MAX_SERVER_ERRORS] times.
     * OTHER: a local problem, fails at once.
     */
    enum class FailureKind { NETWORK, SERVER, OTHER }

    enum class Decision { RETRY, FAIL }

    /**
     * Looks through the whole chain of causes, because RxJava wraps a checked [IOException] in a RuntimeException.
     * An HTTP error wins over everything else. Errors of an overloaded or restarting server (5xx except 507, 408,
     * 429) are treated like a lost network: the upload waits and goes on without a limit. Other HTTP errors and
     * certificate problems count against [MAX_SERVER_ERRORS]. Any other [SSLException] is a network error: on
     * Android a connection that drops in the middle of an HTTPS transfer arrives as an SSLException.
     */
    fun classify(error: Throwable): FailureKind {
        val causes = causeChain(error)
        val httpCode = causes.firstNotNullOfOrNull(::httpCodeOf)
        return when {
            httpCode != null -> if (isTransientHttpCode(httpCode)) FailureKind.NETWORK else FailureKind.SERVER
            isCertificateProblem(causes) -> FailureKind.SERVER
            causes.any { it is IOException && it !is FileNotFoundException } -> FailureKind.NETWORK
            else -> FailureKind.OTHER
        }
    }

    private fun causeChain(error: Throwable): List<Throwable> =
        generateSequence(error) { it.cause }.take(MAX_CAUSE_DEPTH).toList()

    private fun isCertificateProblem(causes: List<Throwable>): Boolean =
        causes.any { it is SSLPeerUnverifiedException || it is UnknownServiceException } ||
            (
                causes.any { it is SSLHandshakeException } &&
                    causes.any { it is CertificateException || it is CertPathValidatorException }
                )

    /**
     * Like [classify], for an error while the file is prepared (copy, compression). A full or read-only disk and
     * a file that cannot be read do not go away by waiting, so they are OTHER; any other IOException, e.g. a lost
     * network while a cloud provider streams the file, is waited for.
     */
    fun classifyPreparation(error: Throwable): FailureKind =
        if (causeChain(error).any(::isUnrecoverableLocal)) FailureKind.OTHER else classify(error)

    private fun isUnrecoverableLocal(error: Throwable): Boolean {
        val errno = (error as? ErrnoException)?.errno
        return error is FileNotFoundException || errno == OsConstants.ENOSPC || errno == OsConstants.EROFS
    }

    private fun httpCodeOf(error: Throwable): Int? =
        when (error) {
            is DavHttpException -> error.code
            is RetrofitHttpException -> error.code()
            else -> null
        }

    private fun isTransientHttpCode(code: Int): Boolean =
        (code >= HTTP_SERVER_ERROR && code != HTTP_INSUFFICIENT_STORAGE) ||
            code == HTTP_REQUEST_TIMEOUT ||
            code == HTTP_TOO_MANY_REQUESTS

    /**
     * @param serverErrorCount server errors of this upload so far, including the one being decided on
     */
    fun decide(kind: FailureKind, serverErrorCount: Int): Decision =
        when {
            kind == FailureKind.NETWORK -> Decision.RETRY
            kind == FailureKind.SERVER && serverErrorCount < MAX_SERVER_ERRORS -> Decision.RETRY
            else -> Decision.FAIL
        }
}
