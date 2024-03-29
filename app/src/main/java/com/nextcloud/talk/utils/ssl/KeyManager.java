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
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.net.ssl.X509KeyManager;

import androidx.annotation.Nullable;

public class KeyManager implements X509KeyManager {
    private static final String TAG = "KeyManager";
    private final X509KeyManager keyManager;

    private UserManager userManager;
    private AppPreferences appPreferences;
    private Context context;

    public KeyManager(X509KeyManager keyManager, UserManager userManager, AppPreferences appPreferences) {
        this.keyManager = keyManager;
        this.userManager = userManager;
        this.appPreferences = appPreferences;

        context = NextcloudTalkApplication.Companion.getSharedApplication().getApplicationContext();
    }

    @Override
    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
        String alias;
        User currentUser = userManager.getCurrentUser().blockingGet();
        if ((currentUser != null &&
            !TextUtils.isEmpty(alias = currentUser.getClientCertificate())) ||
            !TextUtils.isEmpty(alias = appPreferences.getTemporaryClientCertAlias())
                && new ArrayList<>(Arrays.asList(getClientAliases())).contains(alias)) {
            return alias;
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
        if (new ArrayList<>(Arrays.asList(getClientAliases())).contains(s)) {
            return getCertificatesForAlias(s);
        }

        return null;
    }

    private String[] getClientAliases() {
        Set<String> aliases = new HashSet<>();
        String alias;
        if (!TextUtils.isEmpty(alias = appPreferences.getTemporaryClientCertAlias())) {
            aliases.add(alias);
        }

        List<User> userEntities = userManager.getUsers().blockingGet();
        for (int i = 0; i < userEntities.size(); i++) {
            if (!TextUtils.isEmpty(alias = userEntities.get(i).getClientCertificate())) {
                aliases.add(alias);
            }
        }

        return aliases.toArray(new String[aliases.size()]);
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
        if (new ArrayList<>(Arrays.asList(getClientAliases())).contains(s)) {
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
