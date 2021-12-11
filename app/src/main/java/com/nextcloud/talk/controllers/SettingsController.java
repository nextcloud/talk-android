/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.security.KeyChain;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Checkable;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;
import com.nextcloud.talk.BuildConfig;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.jobs.AccountRemovalWorker;
import com.nextcloud.talk.jobs.ContactAddressBookWorker;
import com.nextcloud.talk.models.database.CapabilitiesUtil;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.LoggingUtils;
import com.nextcloud.talk.utils.NotificationUtils;
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
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.emoji.widget.EmojiTextView;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.MediaType;
import okhttp3.RequestBody;

@AutoInjector(NextcloudTalkApplication.class)
public class SettingsController extends BaseController {

    public static final String TAG = "SettingsController";
    private static final int ID_REMOVE_ACCOUNT_WARNING_DIALOG = 0;
    @BindView(R.id.settings_screen)
    MaterialPreferenceScreen settingsScreen;
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
    @BindView(R.id.avatarContainer)
    RelativeLayout avatarContainer;
    @BindView(R.id.avatar_image)
    SimpleDraweeView avatarImageView;
    @BindView(R.id.display_name_text)
    EmojiTextView displayNameTextView;
    @BindView(R.id.base_url_text)
    TextView baseUrlTextView;
    @BindView(R.id.server_age_warning_text_card)
    MaterialCardView serverAgeCardView;
    @BindView(R.id.server_age_warning_text)
    TextView serverAgeTextView;
    @BindView(R.id.server_age_warning_icon)
    ImageView serverAgeIcon;
    @BindView(R.id.settings_notifications_category)
    MaterialPreferenceCategory notificationsCategory;
    @BindView(R.id.settings_call_sound)
    MaterialStandardPreference settingsCallSound;
    @BindView(R.id.settings_message_sound)
    MaterialStandardPreference settingsMessageSound;
    @BindView(R.id.settings_remove_account)
    MaterialStandardPreference removeAccountButton;
    @BindView(R.id.settings_reauthorize)
    MaterialStandardPreference reauthorizeButton;
    @BindView(R.id.message_view)
    MaterialPreferenceCategory messageView;
    @BindView(R.id.settings_client_cert)
    MaterialStandardPreference certificateSetup;
    @BindView(R.id.settings_incognito_keyboard)
    MaterialSwitchPreference incognitoKeyboardSwitchPreference;
    @BindView(R.id.settings_screen_security)
    MaterialSwitchPreference screenSecuritySwitchPreference;
    @BindView(R.id.settings_screen_lock)
    MaterialSwitchPreference screenLockSwitchPreference;
    @BindView(R.id.settings_screen_lock_timeout)
    MaterialChoicePreference screenLockTimeoutChoicePreference;
    @BindView(R.id.settings_phone_book_integration)
    MaterialSwitchPreference phoneBookIntegrationPreference;
    @BindView(R.id.settings_read_privacy)
    MaterialSwitchPreference readPrivacyPreference;

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
    private OnPreferenceValueChangedListener<Boolean> readPrivacyChangeListener;
    private OnPreferenceValueChangedListener<Boolean> phoneBookIntegrationChangeListener;

    private Disposable profileQueryDisposable;
    private Disposable dbQueryDisposable;

    @NonNull
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
        appPreferences.registerPhoneBookIntegrationChangeListener(
                phoneBookIntegrationChangeListener = new PhoneBookIntegrationChangeListener(this));
        appPreferences.registerReadPrivacyChangeListener(readPrivacyChangeListener = new ReadPrivacyChangeListener());

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

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            incognitoKeyboardSwitchPreference.setVisibility(View.GONE);
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            screenLockSwitchPreference.setVisibility(View.GONE);
            screenLockTimeoutChoicePreference.setVisibility(View.GONE);
        } else {
            screenLockSwitchPreference.setSummary(String.format(Locale.getDefault(),
                    getResources().getString(R.string.nc_settings_screen_lock_desc),
                    getResources().getString(R.string.nc_app_product_name)));
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            settingsCallSound.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID);
                intent.putExtra(Settings.EXTRA_CHANNEL_ID,
                                NotificationUtils.INSTANCE.getNOTIFICATION_CHANNEL_CALLS_V4());
                startActivity(intent);
            });

            settingsMessageSound.setOnClickListener(v -> {
                Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, BuildConfig.APPLICATION_ID);
                intent.putExtra(Settings.EXTRA_CHANNEL_ID,
                                NotificationUtils.INSTANCE.getNOTIFICATION_CHANNEL_MESSAGES_V3());
                startActivity(intent);
            });

        } else {

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

        }

        if (CapabilitiesUtil.isPhoneBookIntegrationAvailable(userUtils.getCurrentUser())) {
            phoneBookIntegrationPreference.setVisibility(View.VISIBLE);
        } else {
            phoneBookIntegrationPreference.setVisibility(View.GONE);
        }

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
        if (dialogId == ID_REMOVE_ACCOUNT_WARNING_DIALOG) {
            showRemoveAccountWarning(savedInstanceState);
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

    private String getRingtoneName(Context context, Uri ringtoneUri) {
        if (ringtoneUri == null) {
            return getResources().getString(R.string.nc_settings_no_ringtone);
        } else if (ringtoneUri.toString().equals(NotificationUtils.INSTANCE.getDEFAULT_CALL_RINGTONE_URI()) ||
            ringtoneUri.toString().equals(NotificationUtils.INSTANCE.getDEFAULT_MESSAGE_RINGTONE_URI())) {
            return getResources().getString(R.string.nc_settings_default_ringtone);
        } else  {
            Ringtone r = RingtoneManager.getRingtone(context, ringtoneUri);
            return r.getTitle(context);
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

        ((Checkable) screenSecuritySwitchPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.getIsScreenSecured());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ((Checkable) incognitoKeyboardSwitchPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.getIsKeyboardIncognito());
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ((Checkable) incognitoKeyboardSwitchPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.getIsKeyboardIncognito());
        }

        if (CapabilitiesUtil.isReadStatusAvailable(userUtils.getCurrentUser())) {
            ((Checkable) readPrivacyPreference.findViewById(R.id.mp_checkable)).setChecked(!CapabilitiesUtil.isReadStatusPrivate(currentUser));
        } else {
            readPrivacyPreference.setVisibility(View.GONE);
        }

        ((Checkable) phoneBookIntegrationPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.isPhoneBookIntegrationEnabled());

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationsCategory.setTitle(getResources().getString(R.string.nc_settings_notification_sounds_post_oreo));
        }

        Uri callRingtoneUri = NotificationUtils.INSTANCE.getCallRingtoneUri(view.getContext(), appPreferences);
        settingsCallSound.setSummary(getRingtoneName(view.getContext(), callRingtoneUri));

        Uri messageRingtoneUri = NotificationUtils.INSTANCE.getMessageRingtoneUri(view.getContext(), appPreferences);
        settingsMessageSound.setSummary(getRingtoneName(view.getContext(), messageRingtoneUri));

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

            if (CapabilitiesUtil.isServerEOL(currentUser)) {
                serverAgeTextView.setTextColor(ContextCompat.getColor(context, R.color.nc_darkRed));
                serverAgeTextView.setText(R.string.nc_settings_server_eol);
                serverAgeIcon.setColorFilter(ContextCompat.getColor(context, R.color.nc_darkRed),
                                             PorterDuff.Mode.SRC_IN);
            } else if (CapabilitiesUtil.isServerAlmostEOL(currentUser)) {
                serverAgeTextView.setTextColor(ContextCompat.getColor(context, R.color.nc_darkYellow));
                serverAgeTextView.setText(R.string.nc_settings_server_almost_eol);
                serverAgeIcon.setColorFilter(ContextCompat.getColor(context, R.color.nc_darkYellow),
                                             PorterDuff.Mode.SRC_IN);
            } else {
                serverAgeCardView.setVisibility(View.GONE);
            }

            reauthorizeButton.addPreferenceClickListener(view14 -> {
                getRouter().pushController(RouterTransaction.with(
                        new WebViewLoginController(currentUser.getBaseUrl(), true))
                        .pushChangeHandler(new VerticalChangeHandler())
                        .popChangeHandler(new VerticalChangeHandler()));
            });

            if (currentUser.getDisplayName() != null) {
                displayNameTextView.setText(currentUser.getDisplayName());
            }
            
            DisplayUtils.loadAvatarImage(currentUser, avatarImageView, false);

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

        avatarContainer.setOnClickListener(v ->
                getRouter()
                        .pushController((RouterTransaction.with(new ProfileController())
                                .pushChangeHandler(new HorizontalChangeHandler())
                                .popChangeHandler(new HorizontalChangeHandler()))));
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
            appPreferences.unregisterReadPrivacyChangeListener(readPrivacyChangeListener);
            appPreferences.unregisterPhoneBookIntegrationChangeListener(phoneBookIntegrationChangeListener);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == ContactAddressBookWorker.REQUEST_PERMISSION &&
                grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            WorkManager
                    .getInstance()
                    .enqueue(new OneTimeWorkRequest.Builder(ContactAddressBookWorker.class).build());
            checkForPhoneNumber();
        } else {
            appPreferences.setPhoneBookIntegration(false);
            ((Checkable) phoneBookIntegrationPreference.findViewById(R.id.mp_checkable)).setChecked(appPreferences.isPhoneBookIntegrationEnabled());
            Toast.makeText(context, context.getResources().getString(
                    R.string.no_phone_book_integration_due_to_permissions),
                    Toast.LENGTH_LONG).show();
        }
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

    private class PhoneBookIntegrationChangeListener implements OnPreferenceValueChangedListener<Boolean> {
        private final Controller controller;

        public PhoneBookIntegrationChangeListener(Controller controller) {
            this.controller = controller;
        }

        @Override
        public void onChanged(Boolean isEnabled) {
            if (isEnabled) {
                if(ContactAddressBookWorker.Companion.checkPermission(controller, context)){
                    checkForPhoneNumber();
                }
            } else {
                ContactAddressBookWorker.Companion.deleteAll();
            }
        }
    }

    private void checkForPhoneNumber() {
        ncApi.getUserData(
                ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                ApiUtils.getUrlForUserProfile(currentUser.getBaseUrl())
        ).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<UserProfileOverall>() {

                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {

                    }

                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull UserProfileOverall userProfileOverall) {
                        if (userProfileOverall.getOcs().getData().getPhone().isEmpty()) {
                            askForPhoneNumber();
                        } else {
                            Log.d(TAG, "phone number already set");
                        }
                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void askForPhoneNumber() {
        final LinearLayout phoneNumberLayoutWrapper = new LinearLayout(getActivity());
        phoneNumberLayoutWrapper.setOrientation(LinearLayout.VERTICAL);
        phoneNumberLayoutWrapper.setPadding(50, 0, 50, 0);

        final TextInputLayout phoneNumberInputLayout = new TextInputLayout(getActivity());
        final EditText phoneNumberField = new EditText(getActivity());

        phoneNumberInputLayout.setHelperTextColor(ColorStateList.valueOf(getResources().getColor(R.color.nc_darkRed)));
        phoneNumberField.setInputType(InputType.TYPE_CLASS_PHONE);
        phoneNumberField.setText("+");
        phoneNumberField.addTextChangedListener(new TextWatcher() {
            public void afterTextChanged(Editable s) {
            }

            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            public void onTextChanged(CharSequence s, int start, int before, int count) {
                phoneNumberInputLayout.setHelperText("");
            }
        });

        phoneNumberInputLayout.addView(phoneNumberField);
        phoneNumberLayoutWrapper.addView(phoneNumberInputLayout);

        AlertDialog dialog = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.nc_settings_phone_book_integration_phone_number_dialog_title)
                .setMessage(R.string.nc_settings_phone_book_integration_phone_number_dialog_description)
                .setView(phoneNumberLayoutWrapper)
                .setPositiveButton(context.getResources().getString(R.string.nc_common_set), null)
                .setNegativeButton(context.getResources().getString(R.string.nc_common_skip), null)
                .create();

        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialogInterface) {
                Button button = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        setPhoneNumber(phoneNumberInputLayout, dialog);
                    }
                });
            }
        });
        dialog.show();
    }

    private void setPhoneNumber(TextInputLayout textInputLayout, AlertDialog dialog) {
        String phoneNumber = textInputLayout.getEditText().getText().toString();

        ncApi.setUserData(ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                ApiUtils.getUrlForUserData(currentUser.getBaseUrl(), currentUser.getUserId()), "phone", phoneNumber
        ).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {

                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {

                    }

                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull GenericOverall genericOverall) {
                        int statusCode = genericOverall.ocs.meta.statusCode;
                        if (statusCode == 200) {
                            dialog.dismiss();
                            Toast.makeText(context, context.getResources().getString(
                                    R.string.nc_settings_phone_book_integration_phone_number_dialog_success),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            textInputLayout.setHelperText(context.getResources().getString(
                                    R.string.nc_settings_phone_book_integration_phone_number_dialog_invalid));
                            Log.d(TAG, "failed to set phoneNumber. statusCode=" + statusCode);
                        }

                    }

                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        textInputLayout.setHelperText(context.getResources().getString(
                                R.string.nc_settings_phone_book_integration_phone_number_dialog_invalid));
                        Log.e(TAG, "setPhoneNumber error", e);
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }

    private class ReadPrivacyChangeListener implements OnPreferenceValueChangedListener<Boolean> {
        @Override
        public void onChanged(Boolean newValue) {
            String booleanValue = newValue ? "0" : "1";
            String json = "{\"key\": \"read_status_privacy\", \"value\" : " + booleanValue + "}";

            ncApi.setReadStatusPrivacy(
                    ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                    ApiUtils.getUrlForUserSettings(currentUser.getBaseUrl()),
                    RequestBody.create(MediaType.parse("application/json"), json))
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<GenericOverall>() {
                        @Override
                        public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                        }

                        @Override
                        public void onNext(@io.reactivex.annotations.NonNull GenericOverall genericOverall) {
                        }

                        @Override
                        public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                            appPreferences.setReadPrivacy(!newValue);
                            ((Checkable) readPrivacyPreference.findViewById(R.id.mp_checkable)).setChecked(!newValue);
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
        }
    }
}
