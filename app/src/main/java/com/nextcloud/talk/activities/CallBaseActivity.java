/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.KeyguardManager;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.nextcloud.talk.BuildConfig;

import androidx.activity.OnBackPressedCallback;

public abstract class CallBaseActivity extends BaseActivity {

    public static final String TAG = "CallBaseActivity";

    public PictureInPictureParams.Builder mPictureInPictureParamsBuilder;
    public Boolean isInPipMode = Boolean.FALSE;
    long onCreateTime;


    private OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (isPipModePossible()) {
                enterPipMode();
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        onCreateTime = System.currentTimeMillis();

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        dismissKeyguard();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (isPipModePossible()) {
            mPictureInPictureParamsBuilder = new PictureInPictureParams.Builder();
        }

        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    public void hideNavigationIfNoPipAvailable(){
        if (!isPipModePossible()) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                                                 View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                                                 View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                                                 View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            suppressFitsSystemWindows();
        }
    }

    void dismissKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager keyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            keyguardManager.requestDismissKeyguard(this, null);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    void enableKeyguard() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(false);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (isInPipMode) {
            finish();
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        long onUserLeaveHintTime = System.currentTimeMillis();
        long diff = onUserLeaveHintTime - onCreateTime;
        Log.d(TAG, "onUserLeaveHintTime - onCreateTime: " + diff);

        if (diff < 3000) {
            Log.d(TAG, "enterPipMode skipped");
        } else {
            enterPipMode();
        }
    }

    void enterPipMode() {
        enableKeyguard();
        if (isPipModePossible()) {
            Rational pipRatio = new Rational(300, 500);
            mPictureInPictureParamsBuilder.setAspectRatio(pipRatio);
            enterPictureInPictureMode(mPictureInPictureParamsBuilder.build());
        } else {
            // we don't support other solutions than PIP to have a call in the background.
            // If PIP is not available the call is ended when user presses the home button.
            Log.d(TAG, "Activity was finished because PIP is not available.");
            finish();
        }
    }

    boolean isPipModePossible() {
            boolean deviceHasPipFeature = getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE);

            AppOpsManager appOpsManager = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            boolean isPipFeatureGranted = appOpsManager.checkOpNoThrow(
                AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
                android.os.Process.myUid(),
                BuildConfig.APPLICATION_ID) == AppOpsManager.MODE_ALLOWED;
            return deviceHasPipFeature && isPipFeatureGranted;
    }

    public abstract void updateUiForPipMode();

    public abstract void updateUiForNormalMode();

    public abstract void suppressFitsSystemWindows();
}
