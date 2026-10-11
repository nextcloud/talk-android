/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs

import io.reactivex.Observable
import io.reactivex.disposables.Disposables
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class AccountRemovalWorkerTest {

    @Test
    fun aSuccessfulRequestWithANullBodyIsRecognizedAsSuccess() {
        // The push proxy answers without a body, Retrofit emits null.
        val request = Observable.unsafeCreate<Void> { observer ->
            observer.onSubscribe(Disposables.empty())
            observer.onNext(unsafeNull())
            observer.onComplete()
        }

        assertNull(AccountRemovalWorker.awaitCompletion(request))
    }

    @Test
    fun aFailedRequestReturnsItsError() {
        val error = RuntimeException("proxy unreachable")

        assertSame(error, AccountRemovalWorker.awaitCompletion(Observable.error<Void>(error)))
    }

    // Kotlin refuses a literal null for the non-null RxJava parameter; the erased generic cast lets it through.
    @Suppress("UNCHECKED_CAST")
    private fun <T> unsafeNull(): T = null as T
}
