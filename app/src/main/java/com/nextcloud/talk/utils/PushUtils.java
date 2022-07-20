/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.models.SignatureVerification;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.push.PushConfigurationState;
import com.nextcloud.talk.models.json.push.PushRegistrationOverall;
import com.nextcloud.talk.users.UserManager;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import org.greenrobot.eventbus.EventBus;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
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
import io.reactivex.annotations.NonNull;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class PushUtils {
    private static final String TAG = "PushUtils";

    @Inject
    UserUtils userUtils;

    @Inject
    UserManager userManager;

    @Inject
    AppPreferences appPreferences;

    @Inject
    EventBus eventBus;

    @Inject
    NcApi ncApi;

    private final File publicKeyFile;
    private final File privateKeyFile;

    private final String proxyServer;

    public PushUtils() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        String keyPath = NextcloudTalkApplication.Companion.getSharedApplication().getDir("PushKeystore", Context.MODE_PRIVATE).getAbsolutePath();
        publicKeyFile = new File(keyPath, "push_key.pub");
        privateKeyFile = new File(keyPath, "push_key.priv");
        proxyServer = appPreferences.getPushServerUrl();
    }

    public SignatureVerification verifySignature(byte[] signatureBytes, byte[] subjectBytes) {
        SignatureVerification signatureVerification = new SignatureVerification();
        signatureVerification.setSignatureValid(false);

        List<User> users = userManager.getUsers().blockingGet();
        try {
            Signature signature = Signature.getInstance("SHA512withRSA");
            if (users != null && users.size() > 0) {
                PublicKey publicKey;
                for (User user : users) {
                    if (user.getPushConfigurationState() != null) {
                        publicKey = (PublicKey) readKeyFromString(true,
                                                                  user.getPushConfigurationState().getUserPublicKey());
                        signature.initVerify(publicKey);
                        signature.update(subjectBytes);
                        if (signature.verify(signatureBytes)) {
                            signatureVerification.setSignatureValid(true);
                            signatureVerification.setUser(user);
                            return signatureVerification;
                        }
                    }
                }
            }
        } catch (NoSuchAlgorithmException e) {
            Log.d(TAG, "No such algorithm");
        } catch (InvalidKeyException e) {
            Log.d(TAG, "Invalid key while trying to verify");
        } catch (SignatureException e) {
            Log.d(TAG, "Signature exception while trying to verify");
        }

        return signatureVerification;
    }

    private int saveKeyToFile(Key key, String path) {
        byte[] encoded = key.getEncoded();

        try {
            if (!new File(path).exists()) {
                if (!new File(path).createNewFile()) {
                    return -1;
                }
            }

            try (FileOutputStream keyFileOutputStream = new FileOutputStream(path)) {
                keyFileOutputStream.write(encoded);
                return 0;
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Failed to save key to file");
        } catch (IOException e) {
            Log.d(TAG, "Failed to save key to file via IOException");
        }

        return -1;
    }

    private String generateSHA512Hash(String pushToken) {
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

    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte individualByte : bytes) {
            result.append(Integer.toString((individualByte & 0xff) + 0x100, 16)
                              .substring(1));
        }
        return result.toString();
    }

    public int generateRsa2048KeyPair() {
        if (!publicKeyFile.exists() && !privateKeyFile.exists()) {

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
                byte[] devicePublicKeyBytes = Base64.encode(devicePublicKey.getEncoded(), Base64.NO_WRAP);
                String devicePublicKeyBase64 = new String(devicePublicKeyBytes);
                devicePublicKeyBase64 = devicePublicKeyBase64.replaceAll("(.{64})", "$1\n");

                devicePublicKeyBase64 = "-----BEGIN PUBLIC KEY-----\n" + devicePublicKeyBase64 + "\n-----END PUBLIC KEY-----\n";

                if (userUtils.anyUserExists()) {
                    for (Object userEntityObject : userUtils.getUsers()) {
                        UserEntity userEntity = (UserEntity) userEntityObject;

                        if (!userEntity.getScheduledForDeletion()) {
                            Map<String, String> nextcloudRegisterPushMap = new HashMap<>();
                            nextcloudRegisterPushMap.put("format", "json");
                            nextcloudRegisterPushMap.put("pushTokenHash", pushTokenHash);
                            nextcloudRegisterPushMap.put("devicePublicKey", devicePublicKeyBase64);
                            nextcloudRegisterPushMap.put("proxyServer", proxyServer);

                            registerDeviceWithNextcloud(nextcloudRegisterPushMap, token, userEntity);
                        }
                    }
                }
            }
        } else {
            Log.e(TAG, "push token was empty when trying to register at nextcloud server");
        }
    }

    private void registerDeviceWithNextcloud(Map<String, String> nextcloudRegisterPushMap, String token, UserEntity userEntity) {
        String credentials = ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken());

        ncApi.registerDeviceForNotificationsWithNextcloud(
            credentials,
            ApiUtils.getUrlNextcloudPush(userEntity.getBaseUrl()),
            nextcloudRegisterPushMap)
            .subscribe(new Observer<PushRegistrationOverall>() {
                @Override
                public void onSubscribe(@NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@NonNull PushRegistrationOverall pushRegistrationOverall) {
                    Log.d(TAG, "pushTokenHash successfully registered at nextcloud server.");

                    Map<String, String> proxyMap = new HashMap<>();
                    proxyMap.put("pushToken", token);
                    proxyMap.put("deviceIdentifier", pushRegistrationOverall.getOcs().getData().
                        getDeviceIdentifier());
                    proxyMap.put("deviceIdentifierSignature", pushRegistrationOverall.getOcs()
                        .getData().getSignature());
                    proxyMap.put("userPublicKey", pushRegistrationOverall.getOcs()
                        .getData().getPublicKey());

                    registerDeviceWithPushProxy(proxyMap, userEntity);
                }

                @Override
                public void onError(@NonNull Throwable e) {
                    eventBus.post(new EventStatus(userEntity.getId(),
                                                  EventStatus.EventType.PUSH_REGISTRATION, false));
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void registerDeviceWithPushProxy(Map<String, String> proxyMap, UserEntity userEntity) {
        ncApi.registerDeviceForNotificationsWithPushProxy(ApiUtils.getUrlPushProxy(), proxyMap)
            .subscribeOn(Schedulers.io())
            .subscribe(new Observer<Void>() {
                @Override
                public void onSubscribe(@NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@NonNull Void aVoid) {
                    try {
                        Log.d(TAG, "pushToken successfully registered at pushproxy.");
                        createOrUpdateUser(proxyMap, userEntity);
                    } catch (IOException e) {
                        Log.e(TAG, "IOException while updating user", e);
                    }
                }

                @Override
                public void onError(@NonNull Throwable e) {
                    eventBus.post(new EventStatus(userEntity.getId(),
                                                  EventStatus.EventType.PUSH_REGISTRATION, false));
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
    }

    private void createOrUpdateUser(Map<String, String> proxyMap, UserEntity userEntity) throws IOException {
        PushConfigurationState pushConfigurationState = new PushConfigurationState();
        pushConfigurationState.setPushToken(proxyMap.get("pushToken"));
        pushConfigurationState.setDeviceIdentifier(proxyMap.get("deviceIdentifier"));
        pushConfigurationState.setDeviceIdentifierSignature(proxyMap.get("deviceIdentifierSignature"));
        pushConfigurationState.setUserPublicKey(proxyMap.get("userPublicKey"));
        pushConfigurationState.setUsesRegularPass(Boolean.FALSE);

        userUtils.createOrUpdateUser(null,
                                     null,
                                     null,
                                     userEntity.getDisplayName(),
                                     LoganSquare.serialize(pushConfigurationState),
                                     null,
                                     null,
                                     userEntity.getId(),
                                     null,
                                     null,
                                     null)
            .subscribe(new Observer<UserEntity>() {
                @Override
                public void onSubscribe(@NonNull Disposable d) {
                    // unused atm
                }

                @Override
                public void onNext(@NonNull UserEntity userEntity) {
                    eventBus.post(new EventStatus(userEntity.getId(), EventStatus.EventType.PUSH_REGISTRATION, true));
                }

                @Override
                public void onError(@NonNull Throwable e) {
                    eventBus.post(new EventStatus(userEntity.getId(), EventStatus.EventType.PUSH_REGISTRATION, false));
                }

                @Override
                public void onComplete() {
                    // unused atm
                }
            });
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
            Log.d(TAG, "No such algorithm while reading key from string");
        } catch (InvalidKeySpecException e) {
            Log.d(TAG, "Invalid key spec while reading key from string");
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

        try (FileInputStream fileInputStream = new FileInputStream(path)) {
            byte[] bytes = new byte[fileInputStream.available()];
            fileInputStream.read(bytes);

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
