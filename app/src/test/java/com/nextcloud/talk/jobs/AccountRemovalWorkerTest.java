/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.jobs;

import org.junit.Test;

import io.reactivex.Observable;
import io.reactivex.disposables.Disposables;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class AccountRemovalWorkerTest {

    @Test
    public void aSuccessfulRequestWithANullBodyIsRecognizedAsSuccess() {
        // The push proxy answers without a body, Retrofit emits null.
        Observable<Void> request = Observable.unsafeCreate(observer -> {
            observer.onSubscribe(Disposables.empty());
            observer.onNext(null);
            observer.onComplete();
        });

        assertNull(AccountRemovalWorker.awaitCompletion(request));
    }

    @Test
    public void aFailedRequestReturnsItsError() {
        RuntimeException error = new RuntimeException("proxy unreachable");

        assertSame(error, AccountRemovalWorker.awaitCompletion(Observable.<Void>error(error)));
    }
}
