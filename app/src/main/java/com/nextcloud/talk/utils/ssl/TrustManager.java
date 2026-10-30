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
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import okio.ByteString;

/**
 * Trusts certificates trusted by the system, plus certificates the user accepted for a specific host.
 * <p>
 * User-accepted certificates are stored under the alias {@code <host>|<sha256 of the certificate>}, so each one is
 * only trusted for the host it was accepted for. Entries stored under any other alias are not honoured.
 */
public class TrustManager extends X509ExtendedTrustManager {
    private static final String TAG = "TrustManager";
    private static final String ALIAS_SEPARATOR = "|";

    @VisibleForTesting
    long userDecisionTimeoutMillis = 15000;

    private final File keystoreFile;
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

    private static String aliasFor(String host, X509Certificate x509Certificate)
        throws CertificateEncodingException {
        String fingerprint = ByteString.of(x509Certificate.getEncoded()).sha256().hex();
        return host.toLowerCase(Locale.ROOT) + ALIAS_SEPARATOR + fingerprint;
    }

    @Nullable
    private static String peerHost(Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSession handshakeSession = ((SSLSocket) socket).getHandshakeSession();
            if (handshakeSession != null) {
                return handshakeSession.getPeerHost();
            }
        }
        return null;
    }

    private boolean isServerTrusted(X509Certificate[] x509Certificates, String s, @Nullable String host) {
        if (systemTrustManager != null) {
            X509Certificate x509Certificate = x509Certificates[0];
            try {
                systemTrustManager.checkServerTrusted(x509Certificates, s);
                return true;
            } catch (CertificateException e) {
                if (host == null) {
                    Log.w(TAG, "Peer host unknown, only accepting certificates trusted by the system");
                    return false;
                }
                String alias;
                try {
                    alias = aliasFor(host, x509Certificate);
                } catch (CertificateEncodingException encodingException) {
                    return false;
                }
                if (!isCertStoredUnder(alias, x509Certificate)) {
                    EventBus.getDefault().post(new CertificateEvent(x509Certificate, host, this, null));
                    long startTime = System.currentTimeMillis();
                    while (!isCertStoredUnder(alias, x509Certificate) && System.currentTimeMillis() <=
                        startTime + userDecisionTimeoutMillis) {
                        //do nothing
                    }
                    return isCertStoredUnder(alias, x509Certificate);
                } else {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isCertInTrustStore(String host, X509Certificate x509Certificate) {
        try {
            return isCertStoredUnder(aliasFor(host, x509Certificate), x509Certificate);
        } catch (CertificateEncodingException exception) {
            return false;
        }
    }

    private boolean isCertStoredUnder(String alias, X509Certificate x509Certificate) {
        if (trustedKeyStore != null) {
            try {
                return x509Certificate.equals(trustedKeyStore.getCertificate(alias));
            } catch (KeyStoreException exception) {
                return false;
            }
        }

        return false;
    }

    public void addCertInTrustStore(String host, X509Certificate x509Certificate) {
        if (trustedKeyStore != null) {
            try (FileOutputStream fileOutputStream = new FileOutputStream(keystoreFile)) {
                trustedKeyStore.setCertificateEntry(aliasFor(host, x509Certificate), x509Certificate);
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
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s, Socket socket)
        throws CertificateException {
        checkClientTrusted(x509Certificates, s);
    }

    @Override
    public void checkClientTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine)
        throws CertificateException {
        checkClientTrusted(x509Certificates, s);
    }

    /**
     * Without a host, only certificates trusted by the system are accepted.
     */
    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s) throws CertificateException {
        if (!isServerTrusted(x509Certificates, s, null)) {
            throw new CertificateException();
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s, Socket socket)
        throws CertificateException {
        if (!isServerTrusted(x509Certificates, s, peerHost(socket))) {
            throw new CertificateException();
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] x509Certificates, String s, SSLEngine sslEngine)
        throws CertificateException {
        if (!isServerTrusted(x509Certificates, s, sslEngine.getPeerHost())) {
            throw new CertificateException();
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return systemTrustManager.getAcceptedIssuers();
    }

    private class HostnameVerifier implements javax.net.ssl.HostnameVerifier {
        private static final String TAG = "HostnameVerifier";
        private final javax.net.ssl.HostnameVerifier defaultHostNameVerifier;

        private HostnameVerifier(javax.net.ssl.HostnameVerifier defaultHostNameVerifier) {
            this.defaultHostNameVerifier = defaultHostNameVerifier;
        }

        @Override
        public boolean verify(String s, SSLSession sslSession) {
            try {
                X509Certificate[] certificates = (X509Certificate[]) sslSession.getPeerCertificates();
                if (certificates.length == 0) {
                    return false;
                }

                if (defaultHostNameVerifier.verify(s, sslSession)) {
                    return true;
                }

                // Certificate has no (matching) Subject Alternative Name, e.g. legacy self-signed
                // certificates that only set the Common Name. Accept it anyway if the user already
                // explicitly trusted this exact certificate for this host via the certificate dialog.
                return isCertInTrustStore(s, certificates[0]);
            } catch (SSLPeerUnverifiedException e) {
                Log.d(TAG, "Couldn't get certificate for host name verification");
                return false;
            }
        }
    }

}
