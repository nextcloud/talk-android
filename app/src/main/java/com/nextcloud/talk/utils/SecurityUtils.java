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

package com.nextcloud.talk.utils;

import android.content.res.Resources;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.security.keystore.UserNotAuthenticatedException;
import android.util.Log;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricPrompt;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;

import javax.crypto.*;
import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.List;

public class SecurityUtils {
    private static final String TAG = "SecurityUtils";
    private static final String CREDENTIALS_KEY = "KEY_CREDENTIALS";
    private static final byte[] SECRET_BYTE_ARRAY = new byte[]{1, 2, 3, 4, 5, 6};

    private static BiometricPrompt.CryptoObject cryptoObject;

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static boolean checkIfWeAreAuthenticated(String screenLockTimeout) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            SecretKey secretKey = (SecretKey) keyStore.getKey(CREDENTIALS_KEY, null);
            Cipher cipher =
                    Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/" + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE);

            // Try encrypting something, it will only work if the user authenticated within
            // the last AUTHENTICATION_DURATION_SECONDS seconds.
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            cipher.doFinal(SECRET_BYTE_ARRAY);

            cryptoObject = new BiometricPrompt.CryptoObject(cipher);
            // If the user has recently authenticated, we will reach here
            return true;
        } catch (UserNotAuthenticatedException e) {
            // User is not authenticated, let's authenticate with device credentials.
            return false;
        } catch (KeyPermanentlyInvalidatedException e) {
            // This happens if the lock screen has been disabled or reset after the key was
            // generated after the key was generated.
            // Shouldnt really happen because we regenerate the key every time an activity
            // is created, but oh well
            // Create key, and attempt again
            createKey(screenLockTimeout);
            return false;
        } catch (BadPaddingException | IllegalBlockSizeException | KeyStoreException |
                CertificateException | UnrecoverableKeyException | IOException
                | NoSuchPaddingException | NoSuchAlgorithmException | InvalidKeyException e) {
            return false;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static BiometricPrompt.CryptoObject getCryptoObject() {
        return cryptoObject;
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public static void createKey(String validity) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            KeyGenerator keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");

            keyGenerator.init(new KeyGenParameterSpec.Builder(CREDENTIALS_KEY,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setRandomizedEncryptionRequired(true)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setUserAuthenticationRequired(true)
                    .setUserAuthenticationValidityDurationSeconds(getIntegerFromStringTimeout(validity))
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());

            keyGenerator.generateKey();
        } catch (NoSuchAlgorithmException | NoSuchProviderException
                | InvalidAlgorithmParameterException | KeyStoreException
                | CertificateException | IOException e) {
            Log.e(TAG, "Failed to create a symmetric key");
        }
    }

    private static int getIntegerFromStringTimeout(String validity) {
        Resources resources = NextcloudTalkApplication.getSharedApplication().getResources();
        List<String> entryValues = Arrays.asList(resources.getStringArray(R.array.screen_lock_timeout_entry_values));
        int[] entryIntValues = resources.getIntArray(R.array.screen_lock_timeout_entry_int_values);
        int indexOfValidity = entryValues.indexOf(validity);
        return entryIntValues[indexOfValidity];
    }
}
