/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.controllers;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;
import autodagger.AutoInjector;
import butterknife.OnClick;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.SecurityUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import org.jetbrains.annotations.NotNull;

import javax.inject.Inject;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@AutoInjector(NextcloudTalkApplication.class)
public class LockedController extends BaseController {
    public static final String TAG = "LockedController";
    private static final int REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS = 112;

    @Inject
    AppPreferences appPreferences;

    @NotNull
    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_locked, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        if (getActivity() != null && getResources() != null) {
            DisplayUtils.applyColorToStatusBar(getActivity(), ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
            DisplayUtils.applyColorToNavigationBar(getActivity().getWindow(), ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
        }
        checkIfWeAreSecure();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @OnClick(R.id.unlockContainer)
    void unlock() {
        checkIfWeAreSecure();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void showBiometricDialog() {
        Context context = getActivity();

        if (context != null) {
            final BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(String.format(context.getString(R.string.nc_biometric_unlock), context.getString(R.string.nc_app_name)))
                    .setNegativeButtonText(context.getString(R.string.nc_cancel))
                    .build();

            Executor executor = Executors.newSingleThreadExecutor();

            final BiometricPrompt biometricPrompt = new BiometricPrompt((FragmentActivity) context, executor,
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                            super.onAuthenticationSucceeded(result);
                            Log.d(TAG, "Fingerprint recognised successfully");
                            new Handler(Looper.getMainLooper()).post(() -> getRouter().popCurrentController());
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            super.onAuthenticationFailed();
                            Log.d(TAG, "Fingerprint not recognised");
                        }

                        @Override
                        public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                            super.onAuthenticationError(errorCode, errString);
                            showAuthenticationScreen();
                        }
                    }
            );

            BiometricPrompt.CryptoObject cryptoObject = SecurityUtils.getCryptoObject();
            if (cryptoObject != null) {
                biometricPrompt.authenticate(promptInfo, cryptoObject);
            } else {
                biometricPrompt.authenticate(promptInfo);
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    private void checkIfWeAreSecure() {
        if (getActivity() != null) {
            KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguardManager != null && keyguardManager.isKeyguardSecure() && appPreferences.getIsScreenLocked()) {
                if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.getScreenLockTimeout())) {
                    showBiometricDialog();
                } else {
                    getRouter().popCurrentController();
                }
            }
        }
    }

    private void showAuthenticationScreen() {
        if (getActivity() != null) {
            KeyguardManager keyguardManager = (KeyguardManager) getActivity().getSystemService(Context.KEYGUARD_SERVICE);
            Intent intent = keyguardManager.createConfirmDeviceCredentialIntent(null, null);
            if (intent != null) {
                startActivityForResult(intent, REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_CONFIRM_DEVICE_CREDENTIALS) {
            if (resultCode == Activity.RESULT_OK) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (SecurityUtils.checkIfWeAreAuthenticated(appPreferences.getScreenLockTimeout())) {
                        Log.d(TAG, "All went well, dismiss locked controller");
                        getRouter().popCurrentController();
                    }
                }
            } else {
                Log.d(TAG, "Authorization failed");
            }
        }
    }

    public AppBarLayoutType getAppBarLayoutType() {
        return AppBarLayoutType.EMPTY;
    }
}
