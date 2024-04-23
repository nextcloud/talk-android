/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.events;

import android.webkit.SslErrorHandler;

import com.nextcloud.talk.utils.ssl.TrustManager;

import java.security.cert.X509Certificate;

import androidx.annotation.Nullable;

public class CertificateEvent {
    private final X509Certificate x509Certificate;
    private final TrustManager trustManager;
    @Nullable
    private final SslErrorHandler sslErrorHandler;

    public CertificateEvent(X509Certificate x509Certificate, TrustManager trustManager,
                            @Nullable SslErrorHandler sslErrorHandler) {
        this.x509Certificate = x509Certificate;
        this.trustManager = trustManager;
        this.sslErrorHandler = sslErrorHandler;
    }

    @Nullable
    public SslErrorHandler getSslErrorHandler() {
        return sslErrorHandler;
    }

    public X509Certificate getX509Certificate() {
        return x509Certificate;
    }

    public TrustManager getTrustManager() {
        return trustManager;
    }
}
