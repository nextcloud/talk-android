/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-FileCopyrightText: 2017 Ricki Hirner
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Influenced by https://gitlab.com/bitfireAT/cert4android/blob/master/src/main/java/at/bitfire/cert4android/CustomCertService.kt
 */
package com.nextcloud.talk.utils.ssl;

import android.content.Context;
import android.util.Log;

import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.CertificateEvent;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;


public class TrustManager implements X509TrustManager {
    private static final String TAG = "TrustManager";

    private File keystoreFile;
    private X509TrustManager systemTrustManager = null;
    private KeyStore trustedKeyStore = null;

    public TrustManager() {
        keystoreFile = new File(NextcloudTalkApplication.Companion.getSharedApplication()
                                    .getDir("CertsKeystore", Context.MODE_PRIVATE),
                                "keystore.bks");
        try {
            trustedKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        } catch (KeyStoreException e) {
            Log.e(TAG, "Trusted key store can't be created.", e);
        }

        if (keystoreFile.exists()) {
            try (FileInputStream fileInputStream = new FileInputStream(keystoreFile)) {
                trustedKeyStore.load(fileInputStream, null);
            } catch (Exception exception) {
                Log.e(TAG, "Error during opening the trusted key store.", exception);
            }
        } else {
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

            for (javax.net.ssl.TrustManager trustManager : trustManagerFactory.getTrustManagers()) {
                if (trustManager instanceof X509TrustManager) {
                    systemTrustManager = (X509TrustManager) trustManager;
                    break;
                }
            }

        } catch (Exception exception) {
            Log.d(TAG, "Failed to load default trust manager " + exception.getLocalizedMessage());
        }

    }

    public javax.net.ssl.HostnameVerifier getHostnameVerifier(javax.net.ssl.HostnameVerifier defaultHostNameVerifier) {
        return new HostnameVerifier(defaultHostNameVerifier);
    }

    private boolean isCertInTrustStore(X509Certificate[] x509Certificates, String s) {
        if (systemTrustManager != null) {
            X509Certificate x509Certificate = x509Certificates[0];
            try {
                systemTrustManager.checkServerTrusted(x509Certificates, s);
                return true;
            } catch (CertificateException e) {
                if (!isCertInTrustStore(x509Certificate)) {
                    EventBus.getDefault().post(new CertificateEvent(x509Certificate, this,
                                                                    null));
                    long startTime = System.currentTimeMillis();
                    while (!isCertInTrustStore(x509Certificate) && System.currentTimeMillis() <=
                        startTime + 15000) {
                        //do nothing
                    }
                    return isCertInTrustStore(x509Certificate);
                } else {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isCertInTrustStore(X509Certificate x509Certificate) {
        if (trustedKeyStore != null) {
            try {
                if (trustedKeyStore.getCertificateAlias(x509Certificate) != null) {
                    return true;
                }
            } catch (KeyStoreException exception) {
                return false;
            }
        }

        return false;
    }

    public void addCertInTrustStore(X509Certificate x509Certificate) {
        if (trustedKeyStore != null) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(keystoreFile)) {
                trustedKeyStore.setCertificateEntry(x509Certificate.getSubjectDN().getName(), x509Certificate);
                trustedKeyStore.store(fileOutputStream, null);
            } catch (Exception exception) {
                Log.d(TAG, "Failed to set certificate entry " + exception.getLocalizedMessage());
            }
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        systemTrustManager.checkClientTrusted(x509Certificates, s);
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        if (!isCertInTrustStore(x509Certificates, s)) {
            throw new CertificateException();
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return systemTrustManager.getAcceptedIssuers();
    }

    private class HostnameVerifier implements javax.net.ssl.HostnameVerifier {
        private static final String TAG = "HostnameVerifier";
        private javax.net.ssl.HostnameVerifier defaultHostNameVerifier;

        private HostnameVerifier(javax.net.ssl.HostnameVerifier defaultHostNameVerifier) {
            this.defaultHostNameVerifier = defaultHostNameVerifier;
        }

        @Override
        public boolean verify(String s, SSLSession sslSession) {

            if (defaultHostNameVerifier.verify(s, sslSession)) {
                return true;
            }

            try {
                X509Certificate[] certificates = (X509Certificate[]) sslSession.getPeerCertificates();
                if (certificates.length > 0 && isCertInTrustStore(certificates, s)) {
                    return true;
                }
            } catch (SSLPeerUnverifiedException e) {
                Log.d(TAG, "Couldn't get certificate for host name verification");
            }

            return false;
        }
    }

}
