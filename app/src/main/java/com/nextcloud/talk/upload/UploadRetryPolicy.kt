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
 * A server that answers "unavailable" is waited for up to [MAX_UNAVAILABLE_MILLIS]. Other errors answered by the
 * server count against [MAX_SERVER_ERRORS].
 */
object UploadRetryPolicy {

    /** Server errors one upload may hit before it is marked as failed. */
    const val MAX_SERVER_ERRORS = 4

    /** How long a server may keep answering "unavailable" without taking data before the upload fails: 24 hours. */
    const val MAX_UNAVAILABLE_MILLIS = 24L * 60 * 60 * 1000

    private const val MAX_CAUSE_DEPTH = 8
    private const val HTTP_REQUEST_TIMEOUT = 408
    private const val HTTP_TOO_MANY_REQUESTS = 429
    private const val HTTP_SERVER_ERROR = 500
    private const val HTTP_NOT_IMPLEMENTED = 501
    private const val HTTP_VERSION_NOT_SUPPORTED = 505
    private const val HTTP_INSUFFICIENT_STORAGE = 507

    // A server that cannot do the request or has a full quota does not change within the few attempts.
    private val PERMANENT_SERVER_ERRORS =
        setOf(HTTP_NOT_IMPLEMENTED, HTTP_VERSION_NOT_SUPPORTED, HTTP_INSUFFICIENT_STORAGE)

    /**
     * NETWORK: no connection, retried without a limit.
     * UNAVAILABLE: the server answered that it is overloaded or restarting (5xx except 501, 505 and 507, 408, 429), retried
     * until [MAX_UNAVAILABLE_MILLIS] pass without progress of the upload.
     * SERVER: an answer or a TLS problem that may not go away, retried up to [MAX_SERVER_ERRORS] times.
     * OTHER: a local problem, fails at once.
     */
    enum class FailureKind { NETWORK, UNAVAILABLE, SERVER, OTHER }

    enum class Decision { RETRY, FAIL }

    /**
     * Looks through the whole chain of causes, because RxJava wraps a checked [IOException] in a RuntimeException.
     * An HTTP error wins over everything else. Errors of an overloaded or restarting server (5xx except 501, 505 and 507, 408,
     * 429) are UNAVAILABLE: the upload waits and goes on, but not for ever. Other HTTP errors and
     * certificate problems count against [MAX_SERVER_ERRORS]. Any other [SSLException] is a network error: on
     * Android a connection that drops in the middle of an HTTPS transfer arrives as an SSLException. A
     * [FileNotFoundException] anywhere in the chain is OTHER: the prepared file cannot be read.
     */
    fun classify(error: Throwable): FailureKind {
        val causes = causeChain(error)
        val httpCode = causes.firstNotNullOfOrNull(::httpCodeOf)
        return when {
            httpCode != null -> if (isTransientHttpCode(httpCode)) FailureKind.UNAVAILABLE else FailureKind.SERVER
            isCertificateProblem(causes) -> FailureKind.SERVER
            // ChunkFromFileRequestBody wraps every error of reading a chunk in a FileNotFoundException, also an
            // IOException of the disk. The file is in noBackupFilesDir: waiting does not repair it.
            causes.any { it is FileNotFoundException } -> FailureKind.OTHER
            causes.any { it is IOException } -> FailureKind.NETWORK
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
        (code >= HTTP_SERVER_ERROR && code !in PERMANENT_SERVER_ERRORS) ||
            code == HTTP_REQUEST_TIMEOUT ||
            code == HTTP_TOO_MANY_REQUESTS

    /**
     * @param serverErrorCount server errors of this upload so far, including the one being decided on
     * @param unavailableForMillis how long the server has been answering "unavailable" without the upload moving on
     */
    fun decide(kind: FailureKind, serverErrorCount: Int, unavailableForMillis: Long): Decision =
        when {
            kind == FailureKind.NETWORK -> Decision.RETRY
            kind == FailureKind.UNAVAILABLE && unavailableForMillis < MAX_UNAVAILABLE_MILLIS -> Decision.RETRY
            kind == FailureKind.SERVER && serverErrorCount < MAX_SERVER_ERRORS -> Decision.RETRY
            else -> Decision.FAIL
        }
}
