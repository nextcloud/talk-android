/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
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

    public TrustManager getMagicTrustManager() {
        return trustManager;
    }
}
