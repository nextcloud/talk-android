/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.ssl;

import android.content.Context;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.text.TextUtils;
import android.util.Log;

import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

import androidx.annotation.Nullable;

public class KeyManager extends X509ExtendedKeyManager {
    private static final String TAG = "KeyManager";
    private final X509KeyManager keyManager;

    private final ClientCertificateUsers clientCertificateUsers;
    private AppPreferences appPreferences;
    private Context context;

    public KeyManager(X509KeyManager keyManager, ClientCertificateUsers clientCertificateUsers,
                      AppPreferences appPreferences) {
        this.keyManager = keyManager;
        this.clientCertificateUsers = clientCertificateUsers;
        this.appPreferences = appPreferences;

        context = NextcloudTalkApplication.Companion.getSharedApplication().getApplicationContext();
    }

    /**
     * Chooses the certificate of the accounts on the server of the connection, see {@link ClientCertificateSelector}. The
     * handshake doesn't know the account of the request, so the default account alone would offer the wrong
     * certificate for the requests of other accounts.
     */
    @Override
    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
        return chooseAlias(getPeerHost(socket), getPeerPort(socket));
    }

    @Override
    public String chooseEngineClientAlias(String[] keyType, Principal[] issuers, SSLEngine engine) {
        return engine == null ? chooseAlias(null, -1) : chooseAlias(engine.getPeerHost(), engine.getPeerPort());
    }

    /**
     * Falls back to the certificate chosen during login, as the account it is for doesn't exist yet.
     */
    @Nullable
    private String chooseAlias(@Nullable String host, int port) {
        String alias = ClientCertificateSelector.selectAlias(host, port, clientCertificateUsers.getUsers());
        if (!TextUtils.isEmpty(alias)) {
            return alias;
        }
        alias = appPreferences.getTemporaryClientCertAlias();
        return TextUtils.isEmpty(alias) ? null : alias;
    }

    private static int getPeerPort(@Nullable Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSession session = ((SSLSocket) socket).getHandshakeSession();
            if (session != null && session.getPeerPort() > 0) {
                return session.getPeerPort();
            }
        }
        if (socket != null && socket.getPort() > 0) {
            return socket.getPort();
        }
        return -1;
    }

    /**
     * The host name the connection was opened for. Unlike InetAddress.getHostName(), the fallback never does a reverse
     * DNS lookup.
     */
    @Nullable
    private static String getPeerHost(@Nullable Socket socket) {
        if (socket instanceof SSLSocket) {
            SSLSession session = ((SSLSocket) socket).getHandshakeSession();
            if (session != null && session.getPeerHost() != null) {
                return session.getPeerHost();
            }
        }
        if (socket != null) {
            SocketAddress address = socket.getRemoteSocketAddress();
            if (address instanceof InetSocketAddress) {
                return ((InetSocketAddress) address).getHostString();
            }
        }
        return null;
    }

    @Override
    public String chooseServerAlias(String s, Principal[] principals, Socket socket) {
        return null;
    }

    private X509Certificate[] getCertificatesForAlias(@Nullable String alias) {
        if (alias != null) {
            GetCertificatesForAliasRunnable getCertificatesForAliasRunnable = new GetCertificatesForAliasRunnable(alias);
            Thread getCertificatesThread = new Thread(getCertificatesForAliasRunnable);
            getCertificatesThread.start();
            try {
                getCertificatesThread.join();
                return getCertificatesForAliasRunnable.getCertificates();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to join the thread while getting certificates: " + e.getLocalizedMessage());
            }
        }

        return null;
    }

    private PrivateKey getPrivateKeyForAlias(@Nullable String alias) {
        if (alias != null) {
            GetPrivateKeyForAliasRunnable getPrivateKeyForAliasRunnable = new GetPrivateKeyForAliasRunnable(alias);
            Thread getPrivateKeyThread = new Thread(getPrivateKeyForAliasRunnable);
            getPrivateKeyThread.start();
            try {
                getPrivateKeyThread.join();
                return getPrivateKeyForAliasRunnable.getPrivateKey();
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed to join the thread while getting private key: " + e.getLocalizedMessage());
            }
        }

        return null;
    }


    @Override
    public X509Certificate[] getCertificateChain(String s) {
        if (isClientAlias(s)) {
            return getCertificatesForAlias(s);
        }

        return null;
    }

    private String[] getClientAliases() {
        Set<String> aliases = new HashSet<>(ClientCertificateSelector.aliasesOf(clientCertificateUsers.getUsers()));
        String alias;
        if (!TextUtils.isEmpty(alias = appPreferences.getTemporaryClientCertAlias())) {
            aliases.add(alias);
        }

        return aliases.toArray(new String[aliases.size()]);
    }

    private boolean isClientAlias(String alias) {
        return Arrays.asList(getClientAliases()).contains(alias);
    }

    @Override
    public String[] getClientAliases(String s, Principal[] principals) {
        return getClientAliases();
    }

    @Override
    public String[] getServerAliases(String s, Principal[] principals) {
        return null;
    }

    @Override
    public PrivateKey getPrivateKey(String s) {
        if (isClientAlias(s)) {
            return getPrivateKeyForAlias(s);
        }

        return null;
    }

    private class GetCertificatesForAliasRunnable implements Runnable {
        private volatile X509Certificate[] certificates;
        private String alias;

        public GetCertificatesForAliasRunnable(String alias) {
            this.alias = alias;
        }

        @Override
        public void run() {
            try {
                certificates = KeyChain.getCertificateChain(context, alias);
            } catch (KeyChainException | InterruptedException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        public X509Certificate[] getCertificates() {
            return certificates;
        }
    }

    private class GetPrivateKeyForAliasRunnable implements Runnable {
        private volatile PrivateKey privateKey;
        private String alias;

        public GetPrivateKeyForAliasRunnable(String alias) {
            this.alias = alias;
        }

        @Override
        public void run() {
            try {
                privateKey = KeyChain.getPrivateKey(context, alias);
            } catch (KeyChainException | InterruptedException e) {
                Log.e(TAG, e.getLocalizedMessage());
            }
        }

        public PrivateKey getPrivateKey() {
            return privateKey;
        }
    }

}
