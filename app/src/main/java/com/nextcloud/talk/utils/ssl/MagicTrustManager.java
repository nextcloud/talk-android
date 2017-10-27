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
 *
 * Influenced by https://gitlab.com/bitfireAT/cert4android/blob/master/src/main/java/at/bitfire/cert4android/CustomCertService.kt
 */

package com.nextcloud.talk.utils.ssl;

import android.content.Context;
import android.util.Log;

import com.nextcloud.talk.application.NextcloudTalkApplication;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class MagicTrustManager implements X509TrustManager {
    private static final String TAG = "MagicTrustManager";

    private File keystoreFile;
    private X509TrustManager systemTrustManager = null;
    private KeyStore trustedKeyStore = null;

    public MagicTrustManager() {
        keystoreFile = new File(NextcloudTalkApplication.getSharedApplication().getDir("CertsKeystore",
                Context.MODE_PRIVATE), "keystore.bks");
        try {
            trustedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            FileInputStream fileInputStream = new FileInputStream(keystoreFile);
            trustedKeyStore.load(fileInputStream, null);
        } catch (Exception exception) {
            try {
                trustedKeyStore.load(null, null);
            } catch (Exception e) {
                Log.d(TAG, "Failed to create in-memory key store " + e.getLocalizedMessage());
            }
        }

        TrustManagerFactory trustManagerFactory = null;
        try {
            trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.
                    getDefaultAlgorithm());

            trustManagerFactory.init((KeyStore) null);

            for (TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager) {
                    systemTrustManager = (X509TrustManager) trustManager;
                    break;
                }
            }

        } catch (Exception exception) {
            Log.d(TAG, "Failed to load default trust manager " + exception.getLocalizedMessage());
        }

    }

    public boolean isCertInTrustStore(X509Certificate x509Certificate) {
        if (systemTrustManager != null) {
            try {
                systemTrustManager.checkServerTrusted(new X509Certificate[]{x509Certificate}, "generic");
                return true;
            } catch (CertificateException e) {
                if (trustedKeyStore != null) {
                    try {
                        if (trustedKeyStore.getCertificateAlias(x509Certificate) != null) {
                            return true;
                        }
                    } catch (KeyStoreException exception) {
                        return false;
                    }
                }

            }
        }
        return false;
    }

    public void addCertInTrustStore(X509Certificate x509Certificate) {
        if (trustedKeyStore != null) {
            try {
                trustedKeyStore.setCertificateEntry(x509Certificate.getSubjectDN().getName(), x509Certificate);
                FileOutputStream fileOutputStream = new FileOutputStream(keystoreFile);
                trustedKeyStore.store(fileOutputStream, null);
            } catch (Exception exception) {
                Log.d(TAG, "Failed to set certificate entry " + exception.getLocalizedMessage());
            }
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        Log.d(TAG, "We don't validate client certificates just yet");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        if (!isCertInTrustStore(x509Certificates[0])) {
            throw new CertificateException();
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
