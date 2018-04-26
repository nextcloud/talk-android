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

package com.nextcloud.talk.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.SignatureVerification;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.push.PushConfigurationState;
import com.nextcloud.talk.models.json.push.PushRegistrationOverall;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

@AutoInjector(NextcloudTalkApplication.class)
public class PushUtils {
    private static final String TAG = "PushUtils";

    @Inject
    UserUtils userUtils;

    @Inject
    AppPreferences appPreferences;

    @Inject
    EventBus eventBus;

    @Inject
    OkHttpClient okHttpClient;

    @Inject
    Retrofit retrofit;

    NcApi ncApi;

    private File keysFile;
    private File publicKeyFile;
    private File privateKeyFile;

    private String proxyServer;

    public PushUtils() {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        keysFile = NextcloudTalkApplication.getSharedApplication().getDir("PushKeyStore", Context.MODE_PRIVATE);

        publicKeyFile = new File(NextcloudTalkApplication.getSharedApplication().getDir("PushKeystore",
                Context.MODE_PRIVATE), "push_key.pub");
        privateKeyFile = new File(NextcloudTalkApplication.getSharedApplication().getDir("PushKeystore",
                Context.MODE_PRIVATE), "push_key.priv");
        proxyServer = NextcloudTalkApplication.getSharedApplication().getResources().
                getString(R.string.nc_push_server_url);
    }

    public SignatureVerification verifySignature(byte[] signatureBytes, byte[] subjectBytes) {
        Signature signature = null;
        PushConfigurationState pushConfigurationState;
        PublicKey publicKey;
        SignatureVerification signatureVerification = new SignatureVerification();
        signatureVerification.setSignatureValid(false);

        List<UserEntity> userEntities = userUtils.getUsers();
        try {
            signature = Signature.getInstance("SHA512withRSA");
            if (userEntities != null && userEntities.size() > 0) {
                for (UserEntity userEntity : userEntities) {
                    if (!TextUtils.isEmpty(userEntity.getPushConfigurationState())) {
                        pushConfigurationState = LoganSquare.parse(userEntity.getPushConfigurationState(),
                                PushConfigurationState.class);
                        publicKey = (PublicKey) readKeyFromString(true,
                                pushConfigurationState.getUserPublicKey());
                        signature.initVerify(publicKey);
                        signature.update(subjectBytes);
                        if (signature.verify(signatureBytes)) {
                            signatureVerification.setSignatureValid(true);
                            signatureVerification.setUserEntity(userEntity);
                            return signatureVerification;
                        }
                    }
                }
            }
        } catch (NoSuchAlgorithmException e) {
            Log.d(TAG, "No such algorithm");
        } catch (IOException e) {
            Log.d(TAG, "Error while trying to parse push configuration state");
        } catch (InvalidKeyException e) {
            Log.d(TAG, "Invalid key while trying to verify");
        } catch (SignatureException e) {
            Log.d(TAG, "Signature exception while trying to verify");
        }

        return signatureVerification;
    }

    private int saveKeyToFile(Key key, String path) {
        byte[] encoded = key.getEncoded();
        FileOutputStream keyFileOutputStream = null;
        try {
            if (!new File(path).exists()) {
                new File(path).createNewFile();
            }
            keyFileOutputStream = new FileOutputStream(path);
            keyFileOutputStream.write(encoded);
            keyFileOutputStream.close();
            return 0;
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Failed to save key to file");
        } catch (IOException e) {
            Log.d(TAG, "Failed to save key to file via IOException");
        }

        return -1;
    }

    public String generateSHA512Hash(String pushToken) {
        MessageDigest messageDigest = null;
        try {
            messageDigest = MessageDigest.getInstance("SHA-512");
            messageDigest.update(pushToken.getBytes());
            return bytesToHex(messageDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            Log.d(TAG, "SHA-512 algorithm not supported");
        }
        return "";
    }

    public String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte individualByte : bytes) {
            result.append(Integer.toString((individualByte & 0xff) + 0x100, 16)
                    .substring(1));
        }
        return result.toString();
    }

    public int generateRsa2048KeyPair() {
        if (!publicKeyFile.exists() && !privateKeyFile.exists()) {
            if (!keysFile.exists()) {
                keysFile.mkdirs();
            }

            KeyPairGenerator keyGen = null;
            try {
                keyGen = KeyPairGenerator.getInstance("RSA");
                keyGen.initialize(2048);

                KeyPair pair = keyGen.generateKeyPair();
                int statusPrivate = saveKeyToFile(pair.getPrivate(), privateKeyFile.getAbsolutePath());
                int statusPublic = saveKeyToFile(pair.getPublic(), publicKeyFile.getAbsolutePath());

                if (statusPrivate == 0 && statusPublic == 0) {
                    // all went well
                    return 0;
                } else {
                    return -2;
                }

            } catch (NoSuchAlgorithmException e) {
                Log.d(TAG, "RSA algorithm not supported");
            }
        } else {
            // We already have the key
            return -1;
        }

        // we failed to generate the key
        return -2;
    }

    public void pushRegistrationToServer() {
        String token = appPreferences.getPushToken();

        if (!TextUtils.isEmpty(token)) {
            String pushTokenHash = generateSHA512Hash(token).toLowerCase();
            PublicKey devicePublicKey = (PublicKey) readKeyFromFile(true);
            if (devicePublicKey != null) {
                byte[] publicKeyBytes = Base64.encode(devicePublicKey.getEncoded(), Base64.NO_WRAP);
                String publicKey = new String(publicKeyBytes);
                publicKey = publicKey.replaceAll("(.{64})", "$1\n");

                publicKey = "-----BEGIN PUBLIC KEY-----\n" + publicKey + "\n-----END PUBLIC KEY-----\n";

                if (userUtils.anyUserExists()) {
                    String providerValue;
                    PushConfigurationState accountPushData = null;
                    for (Object userEntityObject : userUtils.getUsers()) {
                        UserEntity userEntity = (UserEntity) userEntityObject;
                        providerValue = userEntity.getPushConfigurationState();
                        if (!TextUtils.isEmpty(providerValue)) {
                            try {
                                accountPushData = LoganSquare.parse(providerValue, PushConfigurationState.class);
                            } catch (IOException e) {
                                Log.d(TAG, "Failed to parse account push data");
                                accountPushData = null;
                            }
                        } else {
                            accountPushData = null;
                        }

                        if (accountPushData != null && !accountPushData.getPushToken().equals(token) &&
                                !userEntity.getScheduledForDeletion() ||
                                TextUtils.isEmpty(providerValue) && !userEntity.getScheduledForDeletion()) {


                            Map<String, String> queryMap = new HashMap<>();
                            queryMap.put("format", "json");
                            queryMap.put("pushTokenHash", pushTokenHash);
                            queryMap.put("devicePublicKey", publicKey);
                            queryMap.put("proxyServer", proxyServer);

                            ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(new
                                    JavaNetCookieJar(new CookieManager())).build()).build().create(NcApi.class);

                            ncApi.registerDeviceForNotificationsWithNextcloud(
                                    ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken()),
                                    ApiUtils.getUrlNextcloudPush(userEntity.getBaseUrl()), queryMap)
                                    .subscribeOn(Schedulers.newThread())
                                    .subscribe(new Observer<PushRegistrationOverall>() {
                                        @Override
                                        public void onSubscribe(Disposable d) {

                                        }

                                        @Override
                                        public void onNext(PushRegistrationOverall pushRegistrationOverall) {
                                            Map<String, String> proxyMap = new HashMap<>();
                                            proxyMap.put("pushToken", token);
                                            proxyMap.put("deviceIdentifier", pushRegistrationOverall.getOcs().getData().
                                                    getDeviceIdentifier());
                                            proxyMap.put("deviceIdentifierSignature", pushRegistrationOverall.getOcs()
                                                    .getData().getSignature());
                                            proxyMap.put("userPublicKey", pushRegistrationOverall.getOcs()
                                                    .getData().getPublicKey());


                                            ncApi.registerDeviceForNotificationsWithProxy(ApiUtils.getCredentials
                                                            (userEntity.getUsername(), userEntity.getToken()),
                                                    ApiUtils.getUrlPushProxy(), proxyMap)
                                                    .subscribeOn(Schedulers.newThread())
                                                    .subscribe(new Observer<Void>() {
                                                        @Override
                                                        public void onSubscribe(Disposable d) {

                                                        }

                                                        @Override
                                                        public void onNext(Void aVoid) {
                                                            PushConfigurationState pushConfigurationState =
                                                                    new PushConfigurationState();
                                                            pushConfigurationState.setPushToken(token);
                                                            pushConfigurationState.setDeviceIdentifier(
                                                                    pushRegistrationOverall.getOcs()
                                                                            .getData().getDeviceIdentifier());
                                                            pushConfigurationState.setDeviceIdentifierSignature(
                                                                    pushRegistrationOverall
                                                                            .getOcs().getData().getSignature());
                                                            pushConfigurationState.setUserPublicKey(
                                                                    pushRegistrationOverall.getOcs()
                                                                            .getData().getPublicKey());
                                                            pushConfigurationState.setUsesRegularPass(false);

                                                            try {
                                                                userUtils.createOrUpdateUser(null,
                                                                        null, null,
                                                                        userEntity.getDisplayName(),
                                                                        LoganSquare.serialize(pushConfigurationState), null,
                                                                        null, userEntity.getId(), null, null)
                                                                        .subscribe(new Observer<UserEntity>() {
                                                                            @Override
                                                                            public void onSubscribe(Disposable d) {

                                                                            }

                                                                            @Override
                                                                            public void onNext(UserEntity userEntity) {
                                                                                eventBus.post(new EventStatus(userEntity.getId(), EventStatus.EventType.PUSH_REGISTRATION, true));
                                                                            }

                                                                            @Override
                                                                            public void onError(Throwable e) {
                                                                                eventBus.post(new EventStatus
                                                                                        (userEntity.getId(),
                                                                                                EventStatus.EventType
                                                                                                        .PUSH_REGISTRATION, false));
                                                                            }

                                                                            @Override
                                                                            public void onComplete() {

                                                                            }
                                                                        });
                                                            } catch (IOException e) {
                                                                Log.e(TAG, "IOException while updating user");
                                                            }


                                                        }

                                                        @Override
                                                        public void onError(Throwable e) {
                                                            eventBus.post(new EventStatus(userEntity.getId(),
                                                                    EventStatus.EventType.PUSH_REGISTRATION, false));
                                                        }

                                                        @Override
                                                        public void onComplete() {

                                                        }
                                                    });
                                        }

                                        @Override
                                        public void onError(Throwable e) {
                                            eventBus.post(new EventStatus(userEntity.getId(),
                                                    EventStatus.EventType.PUSH_REGISTRATION, false));

                                        }

                                        @Override
                                        public void onComplete() {
                                        }
                                    });
                        }
                    }
                }
            }
        }
    }

    private Key readKeyFromString(boolean readPublicKey, String keyString) {
        if (readPublicKey) {
            keyString = keyString.replaceAll("\\n", "").replace("-----BEGIN PUBLIC KEY-----",
                    "").replace("-----END PUBLIC KEY-----", "");
        } else {
            keyString = keyString.replaceAll("\\n", "").replace("-----BEGIN PRIVATE KEY-----",
                    "").replace("-----END PRIVATE KEY-----", "");
        }

        KeyFactory keyFactory = null;
        try {
            keyFactory = KeyFactory.getInstance("RSA");
            if (readPublicKey) {
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(Base64.decode(keyString, Base64.DEFAULT));
                return keyFactory.generatePublic(keySpec);
            } else {
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.decode(keyString, Base64.DEFAULT));
                return keyFactory.generatePrivate(keySpec);
            }

        } catch (NoSuchAlgorithmException e) {
            Log.d("TAG", "No such algorithm while reading key from string");
        } catch (InvalidKeySpecException e) {
            Log.d("TAG", "Invalid key spec while reading key from string");
        }

        return null;
    }

    public Key readKeyFromFile(boolean readPublicKey) {
        String path;

        if (readPublicKey) {
            path = publicKeyFile.getAbsolutePath();
        } else {
            path = privateKeyFile.getAbsolutePath();
        }

        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(path);
            byte[] bytes = new byte[fileInputStream.available()];
            fileInputStream.read(bytes);
            fileInputStream.close();

            KeyFactory keyFactory = KeyFactory.getInstance("RSA");

            if (readPublicKey) {
                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(bytes);
                return keyFactory.generatePublic(keySpec);
            } else {
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(bytes);
                return keyFactory.generatePrivate(keySpec);
            }

        } catch (FileNotFoundException e) {
            Log.d(TAG, "Failed to find path while reading the Key");
        } catch (IOException e) {
            Log.d(TAG, "IOException while reading the key");
        } catch (InvalidKeySpecException e) {
            Log.d(TAG, "InvalidKeySpecException while reading the key");
        } catch (NoSuchAlgorithmException e) {
            Log.d(TAG, "RSA algorithm not supported");
        }

        return null;
    }
}
