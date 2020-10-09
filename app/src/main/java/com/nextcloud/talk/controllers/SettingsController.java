/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Checkable;
import android.widget.TextView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.bluelinelabs.logansquare.LoganSquare;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.BuildConfig;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.jobs.AccountRemovalWorker;
import com.nextcloud.talk.models.RingtoneSettings;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.DoNotDisturbUtils;
import com.nextcloud.talk.utils.LoggingUtils;
import com.nextcloud.talk.utils.SecurityUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.preferences.MagicUserInputModule;
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder;
import com.yarolegovich.lovelydialog.LovelySaveStateHandler;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;
import com.yarolegovich.mp.MaterialChoicePreference;
import com.yarolegovich.mp.MaterialEditTextPreference;
import com.yarolegovich.mp.MaterialPreferenceCategory;
import com.yarolegovich.mp.MaterialPreferenceScreen;
import com.yarolegovich.mp.MaterialStandardPreference;
import com.yarolegovich.mp.MaterialSwitchPreference;

import net.orange_box.storebox.listeners.OnPreferenceValueChangedListener;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.emoji.widget.EmojiTextView;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class SettingsController extends BaseController {

    public static final String TAG = "SettingsController";
    private static final int ID_REMOVE_ACCOUNT_WARNING_DIALOG = 0;
    @BindView(R.id.settings_screen)
    MaterialPreferenceScreen settingsScreen;
    @BindView(R.id.settings_proxy_choice)
    MaterialChoicePreference proxyChoice;
    @BindView(R.id.settings_proxy_port_edit)
    MaterialEditTextPreference proxyPortEditText;
    @BindView(R.id.settings_licence)
    MaterialStandardPreference licenceButton;
    @BindView(R.id.settings_privacy)
    MaterialStandardPreference privacyButton;
    @BindView(R.id.settings_source_code)
    MaterialStandardPreference sourceCodeButton;
    @BindView(R.id.settings_version)
    MaterialStandardPreference versionInfo;
    @BindView(R.id.avatar_image)
    SimpleDraweeView avatarImageView;
    @BindView(R.id.display_name_text)
    EmojiTextView displayNameTextView;
    @BindView(R.id.base_url_text)
    TextView baseUrlTextView;
    @BindView(R.id.settings_call_sound)
    MaterialStandardPreference settingsCallSound;
    @BindView(R.id.settings_message_sound)
    MaterialStandardPreference settingsMessageSound;
    @BindView(R.id.settings_remove_account)
    MaterialStandardPreference removeAccountButton;
    @BindView(R.id.settings_switch)
    MaterialStandardPreference switchAccountButton;
    @BindView(R.id.settings_reauthorize)
    MaterialStandardPreference reauthorizeButton;
    @BindView(R.id.settings_add_account)
    MaterialStandardPreference addAccountButton;
    @BindView(R.id.message_view)
    MaterialPreferenceCategory messageView;
    @BindView(R.id.settings_client_cert)
    MaterialStandardPreference certificateSetup;
    @BindView(R.id.settings_always_vibrate)
    MaterialSwitchPreference shouldVibrateSwitchPreference;
    @BindView(R.id.settings_incognito_keyboard)
    MaterialSwitchPreference incognitoKeyboardSwitchPreference;
    @BindView(R.id.settings_screen_security)
    MaterialSwitchPreference screenSecuritySwitchPreference;
    @BindView(R.id.settings_link_previews)
    MaterialSwitchPreference linkPreviewsSwitchPreference;
    @BindView(R.id.settings_screen_lock)
    MaterialSwitchPreference screenLockSwitchPreference;
    @BindView(R.id.settings_screen_lock_timeout)
    MaterialChoicePreference screenLockTimeoutChoicePreference;

    @BindView(R.id.message_text)
    TextView messageText;
    @Inject
    AppPreferences appPreferences;
    @Inject
    NcApi ncApi;
    @Inject
    UserUtils userUtils;
    @Inject
    Context context;
    private LovelySaveStateHandler saveStateHandler;
    private UserEntity currentUser;
    private String credentials;
    private OnPreferenceValueChangedListener<String> proxyTypeChangeListener;
    private OnPreferenceValueChangedListener<Boolean> proxyCredentialsChangeListener;
    private OnPreferenceValueChangedListener<Boolean> screenSecurityChangeListener;
    private OnPreferenceValueChangedListener<Boolean> screenLockChangeListener;
    private OnPreferenceValueChangedListener<String> screenLockTimeoutChangeListener;
    private OnPreferenceValueChangedListener<String> themeChangeListener;

    private Disposable profileQueryDisposable;
    private Disposable dbQueryDisposable;

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_settings, container, false);
    }

    private void getCurrentUser() {
        currentUser = userUtils.getCurrentUser();
        credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        setHasOptionsMenu(true);

        ViewCompat.setTransitionName(avatarImageView, "userAvatar.transitionTag");
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        getCurrentUser();

        if (saveStateHandler == null) {
            saveStateHandler = new LovelySaveStateHandler();
        }

        appPreferences.registerProxyTypeListener(proxyTypeChangeListener = new ProxyTypeChangeListener());
        appPreferences.registerProxyCredentialsListener(proxyCredentialsChangeListener = new ProxyCredentialsChangeListener());
        appPreferences.registerScreenSecurityListener(screenSecurityChangeListener = new ScreenSecurityChangeListener());
        appPreferences.registerScreenLockListener(screenLockChangeListener = new ScreenLockListener());
        appPreferences.registerScreenLockTimeoutListener(screenLockTimeoutChangeListener = new ScreenLockTimeoutListener());
        appPreferences.registerThemeChangeListener(themeChangeListener = new ThemeChangeListener());

        List<String> listWithIntFields = new ArrayList<>();
        listWithIntFields.add("proxy_port");

        settingsScreen.setUserInputModule(new MagicUserInputModule(getActivity(), listWithIntFields));
        settingsScreen.setVisibilityController(R.id.settings_proxy_use_credentials,
                Arrays.asList(R.id.settings_proxy_username_edit, R.id.settings_proxy_password_edit),
                true);

        if (!TextUtils.isEmpty(getResources().getString(R.string.nc_gpl3_url))) {
            licenceButton.addPreferenceClickListener(view1 -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().
                        getString(R.string.nc_gpl3_url)));
                startActivity(browserIntent);
            });
        } else {
            licenceButton.setVisibility(View.GONE);
        }

        if (!DoNotDisturbUtils.INSTANCE.hasVibrator()) {
            shouldVibrateSwitchPreference.setVisibility(View.GONE);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            incognitoKeyboardSwitchPreference.setVisibility(View.GONE);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            screenLockSwitchPreference.setVisibility(View.GONE);
            screenLockTimeoutChoicePreference.setVisibility(View.GONE);
        } else {
            screenLockSwitchPreference.setSummary(String.format(Locale.getDefault(),
                    getResources().getString(R.string.nc_settings_screen_lock_desc),
                    getResources().getString(R.string.nc_app_name)));
        }


        if (!TextUtils.isEmpty(getResources().getString(R.string.nc_privacy_url))) {
            privacyButton.addPreferenceClickListener(view12 -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().
                        getString(R.string.nc_privacy_url)));
                startActivity(browserIntent);
            });
        } else {
            privacyButton.setVisibility(View.GONE);
        }

        if (!TextUtils.isEmpty(getResources().getString(R.string.nc_source_code_url))) {
            sourceCodeButton.addPreferenceClickListener(view13 -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources().
                        getString(R.string.nc_source_code_url)));
                startActivity(browserIntent);
            });
        } else {
            sourceCodeButton.setVisibility(View.GONE);
        }

        versionInfo.setSummary("v" + BuildConfig.VERSION_NAME);

        settingsCallSound.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putBoolean(BundleKeys.INSTANCE.getKEY_ARE_CALL_SOUNDS(), true);
            getRouter().pushController(RouterTransaction.with(new RingtoneSelectionController(bundle))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));
        });

        settingsMessageSound.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putBoolean(BundleKeys.INSTANCE.getKEY_ARE_CALL_SOUNDS(), false);
            getRouter().pushController(RouterTransaction.with(new RingtoneSelectionController(bundle))
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler()));
        });

        if (getResources().getBoolean(R.bool.multiaccount_support)) {
            addAccountButton.addPreferenceClickListener(view15 -> {
                getRouter().pushController(RouterTransaction.with(new
                        ServerSelectionController()).pushChangeHandler(new VerticalChangeHandler())
                        .popChangeHandler(new VerticalChangeHandler()));
            });
        } else {
            addAccountButton.setVisibility(View.GONE);
        }

        switchAccountButton.addPreferenceClickListener(view16 -> {
            getRouter().pushController(RouterTransaction.with(new
                    SwitchAccountController()).pushChangeHandler(new VerticalChangeHandler())
                    .popChangeHandler(new VerticalChangeHandler()));
        });

        String host = null;
        int port = -1;

        URI uri;
        try {
            uri = new URI(currentUser.getBaseUrl());
            host = uri.getHost();
            port = uri.getPort();
        } catch (URISyntaxException e) {
            Log.e(TAG, "Failed to create uri");
        }

        String finalHost = host;
        int finalPort = port;
        certificateSetup.addPreferenceClickListener(v -> KeyChain.choosePrivateKeyAlias(Objects.requireNonNull(getActivity()), alias -> {
            String finalAlias = alias;
            getActivity().runOnUiThread(() -> {
                if (finalAlias != null) {
                    certificateSetup.setTitle(R.string.nc_client_cert_change);
                } else {
                    certificateSetup.setTitle(R.string.nc_client_cert_setup);
                }
            });

            if (alias == null) {
                alias = "";
            }


            userUtils.createOrUpdateUser(null, null, null, null, null, null, null, currentUser.getId(),
                    null, alias, null);
        }, new String[]{"RSA", "EC"}, null, finalHost, finalPort, currentUser.getClientCertificate
                ()));
    }

    private void showLovelyDialog(int dialogId, Bundle savedInstanceState) {
        switch (dialogId) {
            case ID_REMOVE_ACCOUNT_WARNING_DIALOG:
                showRemoveAccountWarning(savedInstanceState);
                break;
            default:
                break;
        }
    }

    @OnClick(R.id.settings_version)
    void sendLogs() {
        if (getResources().getBoolean(R.bool.nc_is_debug)) {
            LoggingUtils.INSTANCE.sendMailWithAttachment(context);
        }
    }

    @Override
    protected void onSaveViewState(@NonNull View view, @NonNull Bundle outState) {
        saveStateHandler.saveInstanceState(outState);
        super.onSaveViewState(view, outState);
    }

    @Override
    protected void onRestoreViewState(@NonNull View view, @NonNull Bundle savedViewState) {
        super.onRestoreViewState(view, savedViewState);
        if (LovelySaveStateHandler.wasDialogOnScreen(savedViewState)) {
            //Dialog won't be restarted automatically, so we need to call this method.
            //Each dialog knows how to restore its state
            showLovelyDialog(LovelySaveStateHandler.getSavedDialogId(savedViewState), savedViewState);
        }
    }

    private void showRemoveAccountWarning(Bundle savedInstanceState) {
        if (getActivity() != null) {
            new LovelyStandardDialog(getActivity(), LovelyStandardDialog.ButtonLayout.HORIZONTAL)
                    .setTopColorRes(R.color.nc_darkRed)
                    .setIcon(DisplayUtils.getTintedDrawable(getResources(),
                            R.drawable.ic_delete_black_24dp, R.color.bg_default))
                    .setPositiveButtonColor(context.getResources().getColor(R.color.nc_darkRed))
                    .setTitle(R.string.nc_settings_remove_account)
                    .setMessage(R.string.nc_settings_remove_confirmation)
                    .setPositiveButton(R.string.nc_settings_remove, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            removeCurrentAccount();
                        }
                    })
                    .setNegativeButton(R.string.nc_cancel, null)
                    .setInstanceStateHandler(ID_REMOVE_ACCOUNT_WARNING_DIALOG, saveStateHandler)
                    .setSavedInstanceState(savedInstanceState)
                    .show();
        }
    }

    private void removeCurrentAccount() {
        boolean otherUserExists = userUtils.scheduleUserForDeletionWithId(currentUser.getId());

        OneTimeWorkRequest accountRemovalWork = new OneTimeWorkRequest.Builder(AccountRemovalWorker.class).build();
        WorkManager.getInstance().enqueue(accountRemovalWork);

        if (otherUserExists && getView() != null) {
            onViewBound(getView());
            onAttach(getView());
        } else if (!otherUserExists) {
            getRouter().setRoot(RouterTransaction.with(
                    new ServerSelectionController())
                    .pushChangeHandler(new VerticalChangeHandler())
                    .popChangeHandler(new VerticalChangeHandler()));
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);

        if (getActionBar() != null) {
            getActionBar().show();
        }

        dispose(null);
        getCurrentUser();

        if (!TextUtils.isEmpty(currentUser.getClientCertificate())) {
            certificateSetup.setTitle(R.string.nc_client_cert_change);
        } else {
            certificateSetup.setTitle(R.string.nc_client_cert_setup);
        }

        if (shouldVibrateSwitchPreference.getVisibility() == View.VISIBLE) {
            ((Checkable) shouldVibrateSwitchPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.getShouldVibrateSetting());
        }

        ((Checkable) screenSecuritySwitchPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.getIsScreenSecured());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((Checkable) incognitoKeyboardSwitchPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.getIsKeyboardIncognito());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ((Checkable) incognitoKeyboardSwitchPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.getIsKeyboardIncognito());
        }

        ((Checkable) linkPreviewsSwitchPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.getAreLinkPreviewsAllowed());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);

            if (keyguardManager.isKeyguardSecure()) {
                screenLockSwitchPreference.setEnabled(true);
                screenLockTimeoutChoicePreference.setEnabled(true);
                ((Checkable) screenLockSwitchPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.getIsScreenLocked());

                screenLockTimeoutChoicePreference.setEnabled(appPreferences.getIsScreenLocked());

                if (appPreferences.getIsScreenLocked()) {
                    screenLockTimeoutChoicePreference.setAlpha(1.0f);
                } else {
                    screenLockTimeoutChoicePreference.setAlpha(0.38f);
                }

                screenLockSwitchPreference.setAlpha(1.0f);
            } else {
                screenLockSwitchPreference.setEnabled(false);
                screenLockTimeoutChoicePreference.setEnabled(false);
                appPreferences.removeScreenLock();
                appPreferences.removeScreenLockTimeout();
                ((Checkable) screenLockSwitchPreference.findViewById(R.id.mp_checkable)).setChecked(false);
                screenLockSwitchPreference.setAlpha(0.38f);
                screenLockTimeoutChoicePreference.setAlpha(0.38f);
            }
        }

        String ringtoneName = "";
        RingtoneSettings ringtoneSettings;
        if (!TextUtils.isEmpty(appPreferences.getCallRingtoneUri())) {
            try {
                ringtoneSettings = LoganSquare.parse(appPreferences.getCallRingtoneUri(), RingtoneSettings.class);
                ringtoneName = ringtoneSettings.getRingtoneName();
            } catch (IOException e) {
                Log.e(TAG, "Failed to parse ringtone name");
            }
            settingsCallSound.setSummary(ringtoneName);
        } else {
            settingsCallSound.setSummary(R.string.nc_settings_default_ringtone);
        }

        ringtoneName = "";

        if (!TextUtils.isEmpty(appPreferences.getMessageRingtoneUri())) {
            try {
                ringtoneSettings = LoganSquare.parse(appPreferences.getMessageRingtoneUri(), RingtoneSettings.class);
                ringtoneName = ringtoneSettings.getRingtoneName();
            } catch (IOException e) {
                Log.e(TAG, "Failed to parse ringtone name");
            }
            settingsMessageSound.setSummary(ringtoneName);
        } else {
            settingsMessageSound.setSummary(R.string.nc_settings_default_ringtone);
        }

        if ("No proxy".equals(appPreferences.getProxyType()) || appPreferences.getProxyType() == null) {
            hideProxySettings();
        } else {
            showProxySettings();
        }

        if (appPreferences.getProxyCredentials()) {
            showProxyCredentials();
        } else {
            hideProxyCredentials();
        }

        if (currentUser != null) {

            baseUrlTextView.setText(Uri.parse(currentUser.getBaseUrl()).getHost());

            reauthorizeButton.addPreferenceClickListener(view14 -> {
                getRouter().pushController(RouterTransaction.with(
                        new WebViewLoginController(currentUser.getBaseUrl(), true))
                        .pushChangeHandler(new VerticalChangeHandler())
                        .popChangeHandler(new VerticalChangeHandler()));
            });

            if (currentUser.getDisplayName() != null) {
                displayNameTextView.setText(currentUser.getDisplayName());
            }

            loadAvatarImage();

            profileQueryDisposable = ncApi.getUserProfile(credentials,
                    ApiUtils.getUrlForUserProfile(currentUser.getBaseUrl()))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(userProfileOverall -> {

                        String displayName = null;
                        if (!TextUtils.isEmpty(userProfileOverall.getOcs().getData()
                                .getDisplayName())) {
                            displayName = userProfileOverall.getOcs().getData()
                                    .getDisplayName();
                        } else if (!TextUtils.isEmpty(userProfileOverall.getOcs().getData()
                                .getDisplayNameAlt())) {
                            displayName = userProfileOverall.getOcs().getData()
                                    .getDisplayNameAlt();
                        }


                        if ((!TextUtils.isEmpty(displayName) && !displayName.equals(currentUser.getDisplayName()))) {

                            dbQueryDisposable = userUtils.createOrUpdateUser(null,
                                    null,
                                    null, displayName, null, null,
                                    null, currentUser.getId(), null, null, null)
                                    .subscribeOn(Schedulers.io())
                                    .observeOn(AndroidSchedulers.mainThread())
                                    .subscribe(userEntityResult -> {
                                                displayNameTextView.setText(userEntityResult.getDisplayName());
                                            },
                                            throwable -> {
                                                dispose(dbQueryDisposable);
                                            }, () -> dispose(dbQueryDisposable));

                        }
                    }, throwable -> {
                        dispose(profileQueryDisposable);
                    }, () -> dispose(profileQueryDisposable));


            removeAccountButton.addPreferenceClickListener(view1 -> {
                showLovelyDialog(ID_REMOVE_ACCOUNT_WARNING_DIALOG, null);
            });
        }


        if (userUtils.getUsers().size() <= 1) {
            switchAccountButton.setVisibility(View.GONE);
        }

        if (ApplicationWideMessageHolder.getInstance().getMessageType() != null) {
            switch (ApplicationWideMessageHolder.getInstance().getMessageType()) {
                case ACCOUNT_UPDATED_NOT_ADDED:
                    messageText.setTextColor(getResources().getColor(R.color.colorPrimary));
                    messageText.setText(getResources().getString(R.string.nc_settings_account_updated));
                    messageView.setVisibility(View.VISIBLE);
                    break;
                case SERVER_WITHOUT_TALK:
                    messageText.setTextColor(getResources().getColor(R.color.nc_darkRed));
                    messageText.setText(getResources().getString(R.string.nc_settings_wrong_account));
                    messageView.setVisibility(View.VISIBLE);
                case ACCOUNT_WAS_IMPORTED:
                    messageText.setTextColor(getResources().getColor(R.color.colorPrimary));
                    messageText.setText(getResources().getString(R.string.nc_Server_account_imported));
                    messageView.setVisibility(View.VISIBLE);
                    break;
                case FAILED_TO_IMPORT_ACCOUNT:
                    messageText.setTextColor(getResources().getColor(R.color.nc_darkRed));
                    messageText.setText(getResources().getString(R.string.nc_server_failed_to_import_account));
                    messageView.setVisibility(View.VISIBLE);
                    break;
                default:
                    messageView.setVisibility(View.GONE);
                    break;
            }
            ApplicationWideMessageHolder.getInstance().setMessageType(null);

            messageView.animate()
                    .translationY(0)
                    .alpha(0.0f)
                    .setDuration(2500)
                    .setStartDelay(5000)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            if (messageView != null) {
                                messageView.setVisibility(View.GONE);
                            }
                        }
                    });

        } else {
            if (messageView != null) {
                messageView.setVisibility(View.GONE);
            }
        }
    }

    private void loadAvatarImage() {
        String avatarId;
        if (!TextUtils.isEmpty(currentUser.getUserId())) {
            avatarId = currentUser.getUserId();
        } else {
            avatarId = currentUser.getUsername();
        }

        DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(avatarImageView.getController())
                .setAutoPlayAnimations(true)
                .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(currentUser.getBaseUrl(),
                        avatarId, R.dimen.avatar_size_big), null))
                .build();
        avatarImageView.setController(draweeController);
    }

    @Override
    public void onDestroy() {
        if (appPreferences != null) {
            appPreferences.unregisterProxyTypeListener(proxyTypeChangeListener);
            appPreferences.unregisterProxyCredentialsListener(proxyCredentialsChangeListener);
            appPreferences.unregisterScreenSecurityListener(screenSecurityChangeListener);
            appPreferences.unregisterScreenLockListener(screenLockChangeListener);
            appPreferences.unregisterScreenLockTimeoutListener(screenLockTimeoutChangeListener);
            appPreferences.unregisterThemeChangeListener(themeChangeListener);
        }
        super.onDestroy();
    }


    private void hideProxySettings() {
        appPreferences.removeProxyHost();
        appPreferences.removeProxyPort();
        appPreferences.removeProxyCredentials();
        appPreferences.removeProxyUsername();
        appPreferences.removeProxyPassword();
        settingsScreen.findViewById(R.id.settings_proxy_host_edit).setVisibility(View.GONE);
        settingsScreen.findViewById(R.id.settings_proxy_port_edit).setVisibility(View.GONE);
        settingsScreen.findViewById(R.id.settings_proxy_use_credentials).setVisibility(View.GONE);
        settingsScreen.findViewById(R.id.settings_proxy_username_edit).setVisibility(View.GONE);
        settingsScreen.findViewById(R.id.settings_proxy_password_edit).setVisibility(View.GONE);
    }

    private void showProxySettings() {
        settingsScreen.findViewById(R.id.settings_proxy_host_edit).setVisibility(View.VISIBLE);
        settingsScreen.findViewById(R.id.settings_proxy_port_edit).setVisibility(View.VISIBLE);
        settingsScreen.findViewById(R.id.settings_proxy_use_credentials).setVisibility(View.VISIBLE);
    }

    private void showProxyCredentials() {
        settingsScreen.findViewById(R.id.settings_proxy_username_edit).setVisibility(View.VISIBLE);
        settingsScreen.findViewById(R.id.settings_proxy_password_edit).setVisibility(View.VISIBLE);
    }

    private void hideProxyCredentials() {
        appPreferences.removeProxyUsername();
        appPreferences.removeProxyPassword();
        settingsScreen.findViewById(R.id.settings_proxy_username_edit).setVisibility(View.GONE);
        settingsScreen.findViewById(R.id.settings_proxy_password_edit).setVisibility(View.GONE);
    }

    private void dispose(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        } else if (disposable == null) {

            if (profileQueryDisposable != null && !profileQueryDisposable.isDisposed()) {
                profileQueryDisposable.dispose();
                profileQueryDisposable = null;
            } else if (profileQueryDisposable != null) {
                profileQueryDisposable = null;
            }

            if (dbQueryDisposable != null && !dbQueryDisposable.isDisposed()) {
                dbQueryDisposable.dispose();
                dbQueryDisposable = null;
            } else if (dbQueryDisposable != null) {
                dbQueryDisposable = null;
            }
        }
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_settings);
    }

    private class ScreenLockTimeoutListener implements OnPreferenceValueChangedListener<String> {

        @Override
        public void onChanged(String newValue) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                SecurityUtils.createKey(appPreferences.getScreenLockTimeout());
            }
        }
    }

    private class ScreenLockListener implements OnPreferenceValueChangedListener<Boolean> {

        @Override
        public void onChanged(Boolean newValue) {
            screenLockTimeoutChoicePreference.setEnabled(newValue);

            if (newValue) {
                screenLockTimeoutChoicePreference.setAlpha(1.0f);
            } else {
                screenLockTimeoutChoicePreference.setAlpha(0.38f);
            }
        }
    }

    private class ScreenSecurityChangeListener implements OnPreferenceValueChangedListener<Boolean> {

        @Override
        public void onChanged(Boolean newValue) {
            if (newValue) {
                if (getActivity() != null) {
                    getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            } else {
                if (getActivity() != null) {
                    getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                }
            }
        }
    }

    private class ProxyCredentialsChangeListener implements OnPreferenceValueChangedListener<Boolean> {

        @Override
        public void onChanged(Boolean newValue) {
            if (newValue) {
                showProxyCredentials();
            } else {
                hideProxyCredentials();
            }
        }
    }

    private class ProxyTypeChangeListener implements OnPreferenceValueChangedListener<String> {

        @Override
        public void onChanged(String newValue) {
            if ("No proxy".equals(newValue)) {
                hideProxySettings();
            } else {
                switch (newValue) {
                    case "HTTP":
                        if (proxyPortEditText != null) {
                            proxyPortEditText.setValue("3128");
                        }
                        break;
                    case "DIRECT":
                        if (proxyPortEditText != null) {
                            proxyPortEditText.setValue("8080");
                        }
                        break;
                    case "SOCKS":
                        if (proxyPortEditText != null) {
                            proxyPortEditText.setValue("1080");
                        }
                        break;
                    default:
                        break;
                }

                showProxySettings();
            }
        }
    }

    private class ThemeChangeListener implements OnPreferenceValueChangedListener<String> {
        @Override
        public void onChanged(String newValue) {
            NextcloudTalkApplication.Companion.setAppTheme(newValue);
        }
    }
}
