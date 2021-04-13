/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * Copyright (C) 2021 Andy Scherzinger (info@andy-scherzinger.de)
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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.security.KeyChain;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.utils.AccountUtils;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder;

import java.security.cert.CertificateException;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;

import org.jetbrains.annotations.NotNull;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class ServerSelectionController extends BaseController {

    public static final String TAG = "ServerSelectionController";

    @BindView(R.id.serverEntryTextInputLayout)
    TextInputLayout serverEntryTextInputLayout;
    @BindView(R.id.serverEntryTextInputEditText)
    TextInputEditText serverEntryTextInputEditText;
    @BindView(R.id.serverEntryProgressBar)
    LinearLayout progressBar;
    @BindView(R.id.error_text)
    TextView errorText;
    @BindView(R.id.host_url_input_helper_text)
    TextView hostUrlInputHelperText;
    @BindView(R.id.helper_text_view)
    TextView providersTextView;
    @BindView(R.id.cert_text_view)
    TextView certTextView;

    @Inject
    NcApi ncApi;

    @Inject
    UserUtils userUtils;

    @Inject
    AppPreferences appPreferences;

    private Disposable statusQueryDisposable;

    @NotNull
    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_server_selection, container, false);
    }

    @SuppressLint("LongLogTag")
    @OnClick(R.id.cert_text_view)
    public void onCertClick() {
        if (getActivity() != null) {
            KeyChain.choosePrivateKeyAlias(getActivity(), alias -> {
                if (alias != null) {
                    appPreferences.setTemporaryClientCertAlias(alias);
                } else {
                    appPreferences.removeTemporaryClientCertAlias();
                }

                setCertTextView();
            }, new String[]{"RSA", "EC"}, null, null, -1, null);
        }
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        hostUrlInputHelperText.setText(String.format(
                getResources().getString(R.string.nc_server_helper_text),
                getResources().getString(R.string.nc_server_product_name))
        );

        serverEntryTextInputLayout.setEndIconOnClickListener(view1 -> checkServerAndProceed());

        if (getResources().getBoolean(R.bool.hide_auth_cert)) {
            certTextView.setVisibility(View.GONE);
        }

        if (getResources().getBoolean(R.bool.hide_provider) ||
                TextUtils.isEmpty(getResources().getString(R.string.nc_providers_url)) && 
                        (TextUtils.isEmpty(getResources().getString(R.string.nc_import_account_type)))) {
            providersTextView.setVisibility(View.INVISIBLE);
        } else {
            if ((TextUtils.isEmpty(getResources
                    ().getString(R.string.nc_import_account_type)) ||
                    AccountUtils.INSTANCE.findAccounts(userUtils.getUsers()).size() == 0) &&
                    userUtils.getUsers().size() == 0) {

                providersTextView.setText(R.string.nc_get_from_provider);
                providersTextView.setOnClickListener(view12 -> {
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getResources()
                            .getString(R.string.nc_providers_url)));
                    startActivity(browserIntent);
                });
            } else if (AccountUtils.INSTANCE.findAccounts(userUtils.getUsers()).size() > 0) {
                if (!TextUtils.isEmpty(AccountUtils.INSTANCE.getAppNameBasedOnPackage(getResources()
                        .getString(R.string.nc_import_accounts_from)))) {
                    if (AccountUtils.INSTANCE.findAccounts(userUtils.getUsers()).size() > 1) {
                        providersTextView.setText(String.format(getResources().getString(R.string
                                .nc_server_import_accounts), AccountUtils.INSTANCE.getAppNameBasedOnPackage(getResources()
                                .getString(R.string.nc_import_accounts_from))));
                    } else {
                        providersTextView.setText(String.format(getResources().getString(R.string
                                .nc_server_import_account), AccountUtils.INSTANCE.getAppNameBasedOnPackage(getResources()
                                .getString(R.string.nc_import_accounts_from))));
                    }
                } else {
                    if (AccountUtils.INSTANCE.findAccounts(userUtils.getUsers()).size() > 1) {
                        providersTextView.setText(getResources().getString(R.string.nc_server_import_accounts_plain));
                    } else {
                        providersTextView.setText(getResources().getString(R.string
                                .nc_server_import_account_plain));
                    }
                }

                providersTextView.setOnClickListener(view13 -> {
                    Bundle bundle = new Bundle();
                    bundle.putBoolean(BundleKeys.INSTANCE.getKEY_IS_ACCOUNT_IMPORT(), true);
                    getRouter().pushController(RouterTransaction.with(
                            new SwitchAccountController(bundle))
                            .pushChangeHandler(new HorizontalChangeHandler())
                            .popChangeHandler(new HorizontalChangeHandler()));
                });
            } else {
                providersTextView.setVisibility(View.INVISIBLE);
            }
        }

        serverEntryTextInputEditText.requestFocus();
        
        if (!TextUtils.isEmpty(getResources().getString(R.string.weblogin_url))) {
            serverEntryTextInputEditText.setText(getResources().getString(R.string.weblogin_url));
            checkServerAndProceed();
        }

        serverEntryTextInputEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                checkServerAndProceed();
            }

            return false;
        });
    }

    private void checkServerAndProceed() {
        dispose();

        String url = serverEntryTextInputEditText.getText().toString().trim();

        serverEntryTextInputEditText.setEnabled(false);
        showProgressBar();
        if (providersTextView.getVisibility() != View.INVISIBLE) {
            providersTextView.setVisibility(View.INVISIBLE);
            certTextView.setVisibility(View.INVISIBLE);
        }

        if (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }

        String queryUrl = url + ApiUtils.getUrlPostfixForStatus();

        if (url.startsWith("http://") || url.startsWith("https://")) {
            checkServer(queryUrl, false);
        } else {
            checkServer("https://" + queryUrl, true);
        }
    }

    private void checkServer(String queryUrl, boolean checkForcedHttps) {
        statusQueryDisposable = ncApi.getServerStatus(queryUrl)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> {
                    String productName = getResources().getString(R.string.nc_server_product_name);

                    String versionString = status.getVersion().substring(0, status.getVersion().indexOf("."));
                    int version = Integer.parseInt(versionString);
                    if (status.isInstalled() && !status.isMaintenance() &&
                            !status.isNeedsUpgrade() &&
                            version >= 13) {

                        getRouter().pushController(RouterTransaction.with(
                                new WebViewLoginController(queryUrl.replace("/status.php", ""),
                                        false))
                                .pushChangeHandler(new HorizontalChangeHandler())
                                .popChangeHandler(new HorizontalChangeHandler()));
                    } else if (!status.isInstalled()) {
                        setErrorText(String.format(
                                getResources().getString(R.string.nc_server_not_installed), productName));
                    } else if (status.isNeedsUpgrade()) {
                        setErrorText(String.format(getResources().
                                        getString(R.string.nc_server_db_upgrade_needed),
                                productName));
                    } else if (status.isMaintenance()) {
                        setErrorText(String.format(getResources().
                                        getString(R.string.nc_server_maintenance),
                                productName));
                    } else if (!status.getVersion().startsWith("13.")) {
                        setErrorText(String.format(getResources().
                                        getString(R.string.nc_server_version),
                                getResources().getString(R.string.nc_app_name),
                                productName));
                    }

                }, throwable -> {
                    if (checkForcedHttps) {
                        checkServer(queryUrl.replace("https://", "http://"), false);
                    } else {
                        if (throwable.getLocalizedMessage() != null) {
                            setErrorText(throwable.getLocalizedMessage());
                        } else if (throwable.getCause() instanceof CertificateException) {
                            setErrorText(getResources().getString(R.string.nc_certificate_error));
                        } else {
                            hideProgressBar();
                        }

                        if (serverEntryTextInputEditText != null) {
                            serverEntryTextInputEditText.setEnabled(true);
                        }

                        if (providersTextView.getVisibility() != View.INVISIBLE) {
                            providersTextView.setVisibility(View.VISIBLE);
                            certTextView.setVisibility(View.VISIBLE);
                        }

                        dispose();
                    }
                }, () -> {
                    hideProgressBar();
                    if (providersTextView.getVisibility() != View.INVISIBLE) {
                        providersTextView.setVisibility(View.VISIBLE);
                        certTextView.setVisibility(View.VISIBLE);
                    }
                    dispose();
                });
    }

    private void setErrorText(String text) {
        errorText.setText(text);
        errorText.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
    }

    private void showProgressBar() {
        errorText.setVisibility(View.GONE);
        progressBar.setVisibility(View.VISIBLE);
    }

    private void hideProgressBar() {
        errorText.setVisibility(View.GONE);
        progressBar.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        if (ApplicationWideMessageHolder.getInstance().getMessageType() != null) {
            if (ApplicationWideMessageHolder.getInstance().getMessageType()
                    .equals(ApplicationWideMessageHolder.MessageType.ACCOUNT_SCHEDULED_FOR_DELETION)) {
                setErrorText(getResources().getString(R.string.nc_account_scheduled_for_deletion));
                ApplicationWideMessageHolder.getInstance().setMessageType(null);
            } else if (ApplicationWideMessageHolder.getInstance().getMessageType()
                    .equals(ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK)) {
                setErrorText(getResources().getString(R.string.nc_settings_no_talk_installed));
            } else if (ApplicationWideMessageHolder.getInstance().getMessageType()
                    .equals(ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT)) {
                setErrorText(getResources().getString(R.string.nc_server_failed_to_import_account));
            }
            ApplicationWideMessageHolder.getInstance().setMessageType(null);
        }

        if (getActivity() != null && getResources() != null) {
            DisplayUtils.applyColorToStatusBar(getActivity(), ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
            DisplayUtils.applyColorToNavigationBar(getActivity().getWindow(), ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
        }

        setCertTextView();
    }

    private void setCertTextView() {
        if (getActivity() != null) {
            getActivity().runOnUiThread(() -> {
                if (!TextUtils.isEmpty(appPreferences.getTemporaryClientCertAlias())) {
                    certTextView.setText(R.string.nc_change_cert_auth);
                } else {
                    certTextView.setText(R.string.nc_configure_cert_auth);
                }

                hideProgressBar();
            });
        }
    }

    @Override
    protected void onDestroyView(@NonNull View view) {
        super.onDestroyView(view);
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dispose();
    }

    private void dispose() {
        if (statusQueryDisposable != null && !statusQueryDisposable.isDisposed()) {
            statusQueryDisposable.dispose();
        }

        statusQueryDisposable = null;
    }

    @Override
    public AppBarLayoutType getAppBarLayoutType() {
        return AppBarLayoutType.EMPTY;
    }
}
