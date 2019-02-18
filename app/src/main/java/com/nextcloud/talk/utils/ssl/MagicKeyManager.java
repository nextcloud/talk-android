/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.utils.ssl;

import android.content.Context;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.Nullable;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import javax.net.ssl.X509KeyManager;
import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.*;

public class MagicKeyManager implements X509KeyManager {
    private static final String TAG = "MagicKeyManager";
    private final X509KeyManager keyManager;

    private UserUtils userUtils;
    private AppPreferences appPreferences;
    private Context context;

    public MagicKeyManager(X509KeyManager keyManager, UserUtils userUtils, AppPreferences appPreferences) {
        this.keyManager = keyManager;
        this.userUtils = userUtils;
        this.appPreferences = appPreferences;

        context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
    }

    @Override
    public String chooseClientAlias(String[] strings, Principal[] principals, Socket socket) {
        String alias;
        if (!TextUtils.isEmpty(alias = userUtils.getCurrentUser().getClientCertificate()) ||
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

        List<UserEntity> userEntities = userUtils.getUsers();
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
