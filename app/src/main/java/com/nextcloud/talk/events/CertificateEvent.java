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
import androidx.annotation.Nullable;
import com.nextcloud.talk.utils.ssl.MagicTrustManager;

import java.security.cert.X509Certificate;

public class CertificateEvent {
    private final X509Certificate x509Certificate;
    private final MagicTrustManager magicTrustManager;
    @Nullable
    private final SslErrorHandler sslErrorHandler;

    public CertificateEvent(X509Certificate x509Certificate, MagicTrustManager magicTrustManager,
                            @Nullable SslErrorHandler sslErrorHandler) {
        this.x509Certificate = x509Certificate;
        this.magicTrustManager = magicTrustManager;
        this.sslErrorHandler = sslErrorHandler;
    }

    @Nullable
    public SslErrorHandler getSslErrorHandler() {
        return sslErrorHandler;
    }

    public X509Certificate getX509Certificate() {
        return x509Certificate;
    }

    public MagicTrustManager getMagicTrustManager() {
        return magicTrustManager;
    }
}
