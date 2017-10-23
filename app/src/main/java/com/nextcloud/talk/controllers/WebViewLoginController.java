/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.controllers;

import android.content.pm.ActivityInfo;
import android.net.http.SslError;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.models.LoginData;
import com.nextcloud.talk.utils.BundleBuilder;
import com.nextcloud.talk.utils.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import io.reactivex.disposables.Disposable;
import io.requery.Persistable;
import io.requery.reactivex.ReactiveEntityStore;

@AutoInjector(NextcloudTalkApplication.class)
public class WebViewLoginController extends BaseController {

    public static final String TAG = "WebViewLoginController";

    private final String PROTOCOL_SUFFIX = "://";
    private final String LOGIN_URL_DATA_KEY_VALUE_SEPARATOR = ":";

    @Inject
    UserUtils userUtils;
    @Inject
    ReactiveEntityStore<Persistable> dataStore;

    @BindView(R.id.webview)
    WebView webView;

    private String assembledPrefix;

    private Disposable userQueryDisposable;

    private String baseUrl;

    public WebViewLoginController(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public WebViewLoginController(Bundle args) {
        super(args);
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_web_view_login, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

        assembledPrefix = getResources().getString(R.string.nc_talk_login_scheme) + PROTOCOL_SUFFIX + "login/";

        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowFileAccessFromFileURLs(false);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUserAgentString(ApiHelper.getUserAgent());
        webView.getSettings().setSaveFormData(false);
        webView.getSettings().setSavePassword(false);
        webView.clearCache(true);
        webView.clearFormData();

        Map<String, String> headers = new HashMap<>();
        headers.put("OCS-APIRequest", "true");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(assembledPrefix)) {
                    parseAndLoginFromWebView(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                super.onReceivedSslError(view, handler, error);
            }

            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
            }
        });

        webView.loadUrl(baseUrl + "/index.php/login/flow", headers);
    }


    private void dispose() {
        if (userQueryDisposable != null && !userQueryDisposable.isDisposed()) {
            userQueryDisposable.dispose();
        }

        userQueryDisposable = null;
    }

    private void parseAndLoginFromWebView(String dataString) {
        LoginData loginData = parseLoginData(assembledPrefix, dataString);

        if (loginData != null) {
            dispose();

            // We use the URL user entered because one provided by the server is NOT reliable
            userQueryDisposable = userUtils.createOrUpdateUser(loginData.getUsername(), loginData.getToken(),
                    baseUrl, null).subscribe(userEntity -> {
                        BundleBuilder bundleBuilder = new BundleBuilder(new Bundle());
                        bundleBuilder.putString(BundleKeys.KEY_USERNAME, userEntity.getUsername());
                        bundleBuilder.putString(BundleKeys.KEY_TOKEN, userEntity.getToken());
                        bundleBuilder.putString(BundleKeys.KEY_BASE_URL, userEntity.getBaseUrl());
                        getRouter().pushController(RouterTransaction.with(new AccountVerificationController
                                (bundleBuilder.build())).pushChangeHandler(new HorizontalChangeHandler())
                                .popChangeHandler(new HorizontalChangeHandler()));
                    }, throwable -> dispose(),
                    this::dispose);
        }
    }

    private LoginData parseLoginData(String prefix, String dataString) {
        if (dataString.length() < prefix.length()) {
            return null;
        }

        LoginData loginData = new LoginData();

        // format is xxx://login/server:xxx&user:xxx&password:xxx
        String data = dataString.substring(prefix.length());

        String[] values = data.split("&");

        if (values.length != 3) {
            return null;
        }

        for (String value : values) {
            if (value.startsWith("user" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
                loginData.setUsername(URLDecoder.decode(
                        value.substring(("user" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length())));
            } else if (value.startsWith("password" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
                loginData.setToken(URLDecoder.decode(
                        value.substring(("password" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length())));
            } else if (value.startsWith("server" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR)) {
                loginData.setServerUrl(URLDecoder.decode(
                        value.substring(("server" + LOGIN_URL_DATA_KEY_VALUE_SEPARATOR).length())));
            } else {
                return null;
            }
        }

        if (!TextUtils.isEmpty(loginData.getServerUrl()) && !TextUtils.isEmpty(loginData.getUsername()) &&
                !TextUtils.isEmpty(loginData.getToken())) {
            return loginData;
        } else {
            return null;
        }
    }

    @Override
    protected void onDestroyView(@NonNull View view) {
        super.onDestroyView(view);
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

}
