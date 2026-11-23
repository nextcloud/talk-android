/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.activities;

import android.annotation.SuppressLint;
import android.app.AppOpsManager;
import android.app.PictureInPictureParams;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
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

    private boolean screenStateReceiverRegistered = false;

    /**
     * On API 31+ the system auto-enters picture in picture on any pause, including the display going to sleep,
     * which would push the call window into PiP behind the keyguard. Auto-enter is gated on the screen being
     * interactive (see {@link #shouldAutoEnterPip(boolean, boolean)}), so react to the screen turning on/off and
     * update the auto-enter flag before the next pause is evaluated.
     */
    private final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePipAutoEnter();
        }
    };

    private final OnBackPressedCallback onBackPressedCallback = new OnBackPressedCallback(true) {
        @Override
        public void handleOnBackPressed() {
            if (shouldEnterPipModeAutomatically()) {
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
                // registered first, so a screen turning on right after the check is not missed
                registerScreenStateReceiver();
                mPictureInPictureParamsBuilder.setAutoEnterEnabled(
                    shouldAutoEnterPip(isAutomaticPipModeSuppressed(), isInteractive()));
            }
            setPictureInPictureParams(mPictureInPictureParamsBuilder.build());
        }

        getOnBackPressedDispatcher().addCallback(this, onBackPressedCallback);
    }

    private void registerScreenStateReceiver() {
        if (screenStateReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateReceiver, filter);
        screenStateReceiverRegistered = true;
    }

    @Override
    public void onDestroy() {
        if (screenStateReceiverRegistered) {
            unregisterReceiver(screenStateReceiver);
            screenStateReceiverRegistered = false;
        }
        super.onDestroy();
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
        } else {
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
                || !shouldEnterPipModeAutomatically()
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
                && shouldEnterPipModeAutomatically()
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
        // On API 26-30, enter PIP manually. On API 31+ auto-enter handles swipe-up/home, and plain
        // backgrounding (e.g. task switch) keeps the activity alive on its own. Deliberately no
        // moveTaskToBack here: onUserLeaveHint also fires when a transient overlay like the
        // permission dialog appears at call start, which would throw the call to the background.
        if (!isInPipMode
                && shouldEnterPipModeAutomatically()
                && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            enterPipMode();
        }
    }

    /**
     * A pause caused by the display going to sleep is not the user leaving the call: entering PiP there calls
     * enableKeyguard(), PiP is refused on the lock screen and the call window ends up hidden behind it. The interactive
     * flag is used and not the keyguard state, because the keyguard shows up some time after the display is off.
     */
    private boolean isInteractive() {
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        return powerManager == null || powerManager.isInteractive();
    }

    void enterPipMode() {
        Log.d(TAG, "enterPipMode: isPipModePossible=" + isPipModePossible() + " isInPipMode=" + isInPipMode);
        if (!isInteractive()) {
            Log.d(TAG, "enterPipMode skipped: the screen is off, this is not the user leaving the call");
            return;
        }
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

    /**
     * Whether picture in picture should not be entered when the user leaves the activity (for example, with the back
     * or home buttons). Picture in picture can still be entered explicitly.
     * <p>
     * {@link #updatePipAutoEnter()} must be called whenever the returned value changes.
     */
    protected boolean isAutomaticPipModeSuppressed() {
        return false;
    }

    private boolean shouldEnterPipModeAutomatically() {
        return isPipModePossible() && !isAutomaticPipModeSuppressed();
    }

    /**
     * Updates whether the system enters picture in picture automatically when leaving the activity.
     */
    void updatePipAutoEnter() {
        // It may be called from a task posted before the activity was destroyed.
        if (isFinishing() || isDestroyed()) {
            return;
        }
        // The builder is only created if picture in picture was possible when the activity was created.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mPictureInPictureParamsBuilder != null) {
            mPictureInPictureParamsBuilder.setAutoEnterEnabled(
                shouldAutoEnterPip(isAutomaticPipModeSuppressed(), isInteractive()));
            setPictureInPictureParams(mPictureInPictureParamsBuilder.build());
        }
    }

    /**
     * Whether the system should automatically enter picture in picture when the activity is paused. Auto-enter
     * is only wanted when the user actually leaves the call (home/recents) with the screen on; a pause caused by
     * the display going to sleep is not the user leaving, and auto-entering PiP there would place the call window
     * behind the keyguard. Kept package-private and pure so the gating is unit tested without a real activity.
     */
    static boolean shouldAutoEnterPip(boolean automaticPipModeSuppressed, boolean interactive) {
        return !automaticPipModeSuppressed && interactive;
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
