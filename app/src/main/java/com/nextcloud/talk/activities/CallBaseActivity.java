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

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (isPipModePossible()) {
                enterPipMode();
            } else {
                moveTaskToBack(true);
            }
        }
    };

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        dismissKeyguard();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (isPipModePossible()) {
            mPictureInPictureParamsBuilder = new PictureInPictureParams.Builder();
            Rational pipRatio = new Rational(300, 500);
            mPictureInPictureParamsBuilder.setAspectRatio(pipRatio);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mPictureInPictureParamsBuilder.setAutoEnterEnabled(true);
            }
            setPictureInPictureParams(mPictureInPictureParamsBuilder.build());
        }

        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    public void hideNavigationIfNoPipAvailable() {
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

    /**
     * On API 29+, fires BEFORE onPause while the window is still fully visible.
     *
     * On API 29-30: enter PIP immediately (no auto-enter available).
     *
     * On API 31+: auto-enter handles swipe-up/home gestures. Task switching
     * (left/right swipe) does NOT trigger auto-enter — we accept no PIP for
     * task switch since the call stays alive in the background via the ICE
     * failure guard in CallActivity.
     */
    @Override
    public void onTopResumedActivityChanged(boolean isTopResumedActivity) {
        super.onTopResumedActivityChanged(isTopResumedActivity);
        Log.d(TAG, "onTopResumedActivityChanged: isTopResumedActivity=" + isTopResumedActivity
                + " isInPictureInPictureMode=" + isInPictureInPictureMode());
        if (isTopResumedActivity || isInPictureInPictureMode()
                || !isPipModePossible()
                || isChangingConfigurations()
                || isFinishing()) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPipMode();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: isInPipMode=" + isInPipMode
                + " isInPictureInPictureMode=" + isInPictureInPictureMode());
        // Fallback for API 26-28 where onTopResumedActivityChanged doesn't exist.
        // On API 29+, onTopResumedActivityChanged already handled this.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && !isInPictureInPictureMode()
                && isPipModePossible()
                && !isChangingConfigurations()
                && !isFinishing()) {
            enterPipMode();
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: isInPipMode=" + isInPipMode + " isFinishing=" + isFinishing());
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        Log.d(TAG, "onUserLeaveHint: isInPipMode=" + isInPipMode
                + " isInPictureInPictureMode=" + isInPictureInPictureMode());
        // On API 26-30, enter PIP manually.
        if (!isInPipMode
                && isPipModePossible()
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPipMode();
            return;
        }
        // On API 31+: if auto-enter didn't handle it (task switch), move the
        // task to back so the activity survives instead of being destroyed
        // (excludeFromRecents + separate taskAffinity causes task death).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !isInPictureInPictureMode()
                && isPipModePossible()) {
            Log.d(TAG, "onUserLeaveHint: not PIP, moving task to back to survive task switch");
            moveTaskToBack(true);
        }
    }

    void enterPipMode() {
        Log.d(TAG, "enterPipMode: isPipModePossible=" + isPipModePossible() + " isInPipMode=" + isInPipMode);
        enableKeyguard();
        if (isPipModePossible()) {
            Rational pipRatio = new Rational(300, 500);
            mPictureInPictureParamsBuilder.setAspectRatio(pipRatio);
            boolean entered = enterPictureInPictureMode(mPictureInPictureParamsBuilder.build());
            Log.d(TAG, "enterPictureInPictureMode returned: " + entered);
        } else {
            // If PIP is not available, move to background instead of finishing
            Log.d(TAG, "PIP is not available, moving call to background.");
            moveTaskToBack(true);
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
