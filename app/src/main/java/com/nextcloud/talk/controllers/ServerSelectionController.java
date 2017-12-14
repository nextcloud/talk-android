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

import android.content.pm.ActivityInfo;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.utils.ErrorMessageHolder;

import java.security.cert.CertificateException;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
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

    @Inject
    NcApi ncApi;

    private Disposable statusQueryDisposable;

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_server_selection, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        textFieldBoxes.setLabelText(getResources().getString(R.string.nc_app_name) + " " + getResources().getString(R.string.nc_appended_server_url));

        serverEntry.requestFocus();

        serverEntry.setOnEditorActionListener((textView, i, keyEvent) -> {
            if (i == EditorInfo.IME_ACTION_DONE) {
                dispose();

                String url = serverEntry.getText().toString().trim();

                if (url.startsWith("http://") || url.startsWith("https://")) {
                    serverEntry.setEnabled(false);
                    progressBar.setVisibility(View.VISIBLE);

                    if (url.endsWith("/")) {
                        url = url.substring(0, url.length() - 1);
                    }

                    String queryUrl = url + ApiHelper.getUrlPostfixForStatus();
                    final String finalServerUrl = url;

                    statusQueryDisposable = ncApi.getServerStatus(queryUrl)
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(status -> {
                                String productName = getResources().getString(R.string.nc_server_product_name);

                                if (status.isInstalled() && !status.isMaintenance() &&
                                        !status.isNeedsUpgrade() &&
                                        status.getVersion().startsWith("13.")) {

                                    getRouter().pushController(RouterTransaction.with(
                                            new WebViewLoginController(finalServerUrl, false))
                                            .pushChangeHandler(new HorizontalChangeHandler())
                                            .popChangeHandler(new HorizontalChangeHandler()));
                                } else if (!status.isInstalled()) {
                                    textFieldBoxes.setError(String.format(
                                            getResources().getString(R.string.nc_server_not_installed), productName),
                                            true);
                                } else if (status.isNeedsUpgrade()) {
                                    textFieldBoxes.setError(String.format(getResources().
                                                    getString(R.string.nc_server_db_upgrade_needed),
                                            productName), true);
                                } else if (status.isMaintenance()) {
                                    textFieldBoxes.setError(String.format(getResources().
                                                    getString(R.string.nc_server_maintenance),
                                            productName),
                                            true);
                                } else if (!status.getVersion().startsWith("13.")) {
                                    textFieldBoxes.setError(String.format(getResources().
                                                    getString(R.string.nc_server_version),
                                            getResources().getString(R.string.nc_app_name)
                                            , productName), true);
                                }

                            }, throwable -> {
                                if (throwable.getLocalizedMessage() != null) {
                                    textFieldBoxes.setError(throwable.getLocalizedMessage(), true);
                                } else if (throwable.getCause() instanceof CertificateException) {
                                    textFieldBoxes.setError(getResources().getString(R.string.nc_certificate_error),
                                            true);
                                }
                                if (serverEntry != null) {
                                    serverEntry.setEnabled(true);
                                }
                                progressBar.setVisibility(View.GONE);

                                dispose();

                            }, () -> {
                                progressBar.setVisibility(View.GONE);
                                dispose();
                            });
                } else {
                    textFieldBoxes.setError(getResources().getString(R.string.nc_server_url_prefix), true);
                    serverEntry.setEnabled(true);
                    return true;
                }

            }

            return false;
        });
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        if (ErrorMessageHolder.getInstance().getMessageType() != null &&
                ErrorMessageHolder.getInstance().getMessageType()
                        .equals(ErrorMessageHolder.ErrorMessageType.ACCOUNT_SCHEDULED_FOR_DELETION)) {
            textFieldBoxes.setError(getResources().getString(R.string.nc_account_scheduled_for_deletion),
                    false);
            ErrorMessageHolder.getInstance().setMessageType(null);
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
