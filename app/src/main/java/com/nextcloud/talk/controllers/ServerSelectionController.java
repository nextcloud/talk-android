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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.security.KeyChain;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.utils.AccountUtils;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder;

import java.security.cert.CertificateException;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import studio.carbonylgroup.textfieldboxes.ExtendedEditText;
import studio.carbonylgroup.textfieldboxes.TextFieldBoxes;

@AutoInjector(NextcloudTalkApplication.class)
public class ServerSelectionController extends BaseController {

    public static final String TAG = "ServerSelectionController";

    @BindView(R.id.extended_edit_text)
    ExtendedEditText serverEntry;
    @BindView(R.id.text_field_boxes)
    TextFieldBoxes textFieldBoxes;
    @BindView(R.id.progress_bar)
    ProgressBar progressBar;
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

        textFieldBoxes.getEndIconImageButton().setBackgroundDrawable(getResources().getDrawable(R.drawable
                .ic_arrow_forward_white_24px));
        textFieldBoxes.getEndIconImageButton().setAlpha(0.5f);
        textFieldBoxes.getEndIconImageButton().setEnabled(false);
        textFieldBoxes.getEndIconImageButton().setVisibility(View.VISIBLE);
        textFieldBoxes.getEndIconImageButton().setOnClickListener(view1 -> checkServerAndProceed());

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

        serverEntry.requestFocus();
        
        if (!TextUtils.isEmpty(getResources().getString(R.string.weblogin_url))) {
            serverEntry.setText(getResources().getString(R.string.weblogin_url));
            checkServerAndProceed();
        }

        serverEntry.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (!textFieldBoxes.isOnError() && !TextUtils.isEmpty(serverEntry.getText())) {
                    toggleProceedButton(true);
                } else {
                    toggleProceedButton(false);
                }
            }
        });

        serverEntry.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                checkServerAndProceed();
            }

            return false;
        });
    }

    private void toggleProceedButton(boolean show) {
        textFieldBoxes.getEndIconImageButton().setEnabled(show);

        if (show) {
            textFieldBoxes.getEndIconImageButton().setAlpha(1f);
        } else {
            textFieldBoxes.getEndIconImageButton().setAlpha(0.5f);
        }
    }

    private void checkServerAndProceed() {
        dispose();

        String url = serverEntry.getText().toString().trim();

        serverEntry.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
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
                        textFieldBoxes.setError(String.format(
                                getResources().getString(R.string.nc_server_not_installed), productName),
                                true);
                        toggleProceedButton(false);
                    } else if (status.isNeedsUpgrade()) {
                        textFieldBoxes.setError(String.format(getResources().
                                        getString(R.string.nc_server_db_upgrade_needed),
                                productName), true);
                        toggleProceedButton(false);
                    } else if (status.isMaintenance()) {
                        textFieldBoxes.setError(String.format(getResources().
                                        getString(R.string.nc_server_maintenance),
                                productName),
                                true);
                        toggleProceedButton(false);
                    } else if (!status.getVersion().startsWith("13.")) {
                        textFieldBoxes.setError(String.format(getResources().
                                        getString(R.string.nc_server_version),
                                getResources().getString(R.string.nc_app_name)
                                , productName), true);
                        toggleProceedButton(false);
                    }

                }, throwable -> {
                    if (checkForcedHttps) {
                        checkServer(queryUrl.replace("https://", "http://"), false);
                    } else {
                        if (throwable.getLocalizedMessage() != null) {
                            textFieldBoxes.setError(throwable.getLocalizedMessage(), true);
                        } else if (throwable.getCause() instanceof CertificateException) {
                            textFieldBoxes.setError(getResources().getString(R.string.nc_certificate_error),
                                    false);
                        }

                        if (serverEntry != null) {
                            serverEntry.setEnabled(true);
                        }

                        progressBar.setVisibility(View.INVISIBLE);
                        if (providersTextView.getVisibility() != View.INVISIBLE) {
                            providersTextView.setVisibility(View.VISIBLE);
                            certTextView.setVisibility(View.VISIBLE);
                        }
                        toggleProceedButton(false);

                        dispose();
                    }
                }, () -> {
                    progressBar.setVisibility(View.INVISIBLE);
                    if (providersTextView.getVisibility() != View.INVISIBLE) {
                        providersTextView.setVisibility(View.VISIBLE);
                        certTextView.setVisibility(View.VISIBLE);
                    }
                    dispose();
                });
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        if (ApplicationWideMessageHolder.getInstance().getMessageType() != null) {
            if (ApplicationWideMessageHolder.getInstance().getMessageType()
                    .equals(ApplicationWideMessageHolder.MessageType.ACCOUNT_SCHEDULED_FOR_DELETION)) {
                textFieldBoxes.setError(getResources().getString(R.string.nc_account_scheduled_for_deletion),
                        false);
                ApplicationWideMessageHolder.getInstance().setMessageType(null);
            } else if (ApplicationWideMessageHolder.getInstance().getMessageType()
                    .equals(ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK)) {
                textFieldBoxes.setError(getResources().getString(R.string.nc_settings_no_talk_installed),
                        false);
            } else if (ApplicationWideMessageHolder.getInstance().getMessageType()
                    .equals(ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT)) {
                textFieldBoxes.setError(getResources().getString(R.string.nc_server_failed_to_import_account),
                        false);
            }
            ApplicationWideMessageHolder.getInstance().setMessageType(null);
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

                textFieldBoxes.setError("", true);
                toggleProceedButton(true);
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


}
