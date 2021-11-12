/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.http.SslCertificate;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.security.KeyChain;
import android.security.KeyChainException;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ClientCertRequest;
import android.webkit.CookieSyncManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.CertificateEvent;
import com.nextcloud.talk.jobs.PushRegistrationWorker;
import com.nextcloud.talk.models.LoginData;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder;
import com.nextcloud.talk.utils.ssl.MagicTrustManager;

import org.greenrobot.eventbus.EventBus;

import java.lang.reflect.Field;
import java.net.CookieManager;
import java.net.URLDecoder;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.res.ResourcesCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;
import butterknife.BindView;
import de.cotech.hw.fido.WebViewFidoBridge;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
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
    AppPreferences appPreferences;
    @Inject
    ReactiveEntityStore<Persistable> dataStore;
    @Inject
    MagicTrustManager magicTrustManager;
    @Inject
    EventBus eventBus;
    @Inject
    CookieManager cookieManager;


    @BindView(R.id.webview)
    WebView webView;

    @BindView(R.id.progress_bar)
    ProgressBar progressBar;

    private String assembledPrefix;

    private Disposable userQueryDisposable;

    private String baseUrl;
    private boolean isPasswordUpdate;

    private String username;
    private String password;
    private int loginStep = 0;

    private boolean automatedLoginAttempted = false;

    private WebViewFidoBridge webViewFidoBridge;

    public WebViewLoginController(String baseUrl, boolean isPasswordUpdate) {
        this.baseUrl = baseUrl;
        this.isPasswordUpdate = isPasswordUpdate;
    }

    public WebViewLoginController(String baseUrl, boolean isPasswordUpdate, String username, String password) {
        this.baseUrl = baseUrl;
        this.isPasswordUpdate = isPasswordUpdate;
        this.username = username;
        this.password = password;
    }

    public WebViewLoginController(Bundle args) {
        super(args);
    }

    private String getWebLoginUserAgent() {
        return Build.MANUFACTURER.substring(0, 1).toUpperCase(Locale.getDefault()) +
                Build.MANUFACTURER.substring(1).toLowerCase(Locale.getDefault()) + " " + Build.MODEL + " ("
                + getResources().getString(R.string.nc_app_product_name) + ")";
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_web_view_login, container, false);
    }

    @SuppressLint("SetJavaScriptEnabled")
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

        assembledPrefix = getResources().getString(R.string.nc_talk_login_scheme) + PROTOCOL_SUFFIX + "login/";

        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowFileAccessFromFileURLs(false);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setJavaScriptCanOpenWindowsAutomatically(false);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setUserAgentString(getWebLoginUserAgent());
        webView.getSettings().setSaveFormData(false);
        webView.getSettings().setSavePassword(false);
        webView.getSettings().setRenderPriority(WebSettings.RenderPriority.HIGH);
        webView.clearCache(true);
        webView.clearFormData();
        webView.clearHistory();
        WebView.clearClientCertPreferences(null);

        webViewFidoBridge = WebViewFidoBridge.createInstanceForWebView((AppCompatActivity) getActivity(), webView);

        CookieSyncManager.createInstance(getActivity());
        android.webkit.CookieManager.getInstance().removeAllCookies(null);

        Map<String, String> headers = new HashMap<>();
        headers.put("OCS-APIRequest", "true");

        webView.setWebViewClient(new WebViewClient() {
            private boolean basePageLoaded;

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                webViewFidoBridge.delegateShouldInterceptRequest(view, request);
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                webViewFidoBridge.delegateOnPageStarted(view, url, favicon);
            }

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
                loginStep++;

                if (!basePageLoaded) {
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }

                    if (webView != null) {
                        webView.setVisibility(View.VISIBLE);
                    }
                    basePageLoaded = true;
                }

                if (!TextUtils.isEmpty(username) && webView != null) {
                    if (loginStep == 1) {
                        webView.loadUrl("javascript: {document.getElementsByClassName('login')[0].click(); };");
                    } else if (!automatedLoginAttempted) {
                        automatedLoginAttempted = true;
                        if (TextUtils.isEmpty(password)) {
                            webView.loadUrl("javascript:var justStore = document.getElementById('user').value = '" + username + "';");
                        } else {
                            webView.loadUrl("javascript: {" +
                                                    "document.getElementById('user').value = '" + username + "';" +
                                                    "document.getElementById('password').value = '" + password + "';" +
                                                    "document.getElementById('submit').click(); };");
                        }
                    }
                }

                super.onPageFinished(view, url);
            }

            @Override
            public void onReceivedClientCertRequest(WebView view, ClientCertRequest request) {
                UserEntity userEntity = userUtils.getCurrentUser();

                String alias = null;
                if (!isPasswordUpdate) {
                    alias = appPreferences.getTemporaryClientCertAlias();
                }

                if (TextUtils.isEmpty(alias) && (userEntity != null)) {
                    alias = userEntity.getClientCertificate();
                }

                if (!TextUtils.isEmpty(alias)) {
                    String finalAlias = alias;
                    new Thread(() -> {
                        try {
                            PrivateKey privateKey = KeyChain.getPrivateKey(getActivity(), finalAlias);
                            X509Certificate[] certificates = KeyChain.getCertificateChain(getActivity(), finalAlias);
                            if (privateKey != null && certificates != null) {
                                request.proceed(privateKey, certificates);
                            } else {
                                request.cancel();
                            }
                        } catch (KeyChainException | InterruptedException e) {
                            request.cancel();
                        }
                    }).start();
                } else {
                    KeyChain.choosePrivateKeyAlias(getActivity(), chosenAlias -> {
                        if (chosenAlias != null) {
                            appPreferences.setTemporaryClientCertAlias(chosenAlias);
                            new Thread(() -> {
                                PrivateKey privateKey = null;
                                try {
                                    privateKey = KeyChain.getPrivateKey(getActivity(), chosenAlias);
                                    X509Certificate[] certificates = KeyChain.getCertificateChain(getActivity(), chosenAlias);
                                    if (privateKey != null && certificates != null) {
                                        request.proceed(privateKey, certificates);
                                    } else {
                                        request.cancel();
                                    }
                                } catch (KeyChainException | InterruptedException e) {
                                    request.cancel();
                                }
                            }).start();
                        } else {
                            request.cancel();
                        }
                    }, new String[]{"RSA", "EC"}, null, request.getHost(), request.getPort(), null);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                try {
                    SslCertificate sslCertificate = error.getCertificate();
                    Field f = sslCertificate.getClass().getDeclaredField("mX509Certificate");
                    f.setAccessible(true);
                    X509Certificate cert = (X509Certificate) f.get(sslCertificate);

                    if (cert == null) {
                        handler.cancel();
                    } else {
                        try {
                            magicTrustManager.checkServerTrusted(new X509Certificate[]{cert}, "generic");
                            handler.proceed();
                        } catch (CertificateException exception) {
                            eventBus.post(new CertificateEvent(cert, magicTrustManager, handler));
                        }
                    }
                } catch (Exception exception) {
                    handler.cancel();
                }
            }

            @Override
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

            UserEntity currentUser = userUtils.getCurrentUser();

            ApplicationWideMessageHolder.MessageType messageType = null;

            if (!isPasswordUpdate && userUtils.getIfUserWithUsernameAndServer(loginData.getUsername(), baseUrl)) {
                messageType = ApplicationWideMessageHolder.MessageType.ACCOUNT_UPDATED_NOT_ADDED;
            }

            if (userUtils.checkIfUserIsScheduledForDeletion(loginData.getUsername(), baseUrl)) {
                ApplicationWideMessageHolder.getInstance().setMessageType(
                        ApplicationWideMessageHolder.MessageType.ACCOUNT_SCHEDULED_FOR_DELETION);

                if (!isPasswordUpdate) {
                    getRouter().popToRoot();
                } else {
                    getRouter().popCurrentController();
                }
            }

            ApplicationWideMessageHolder.MessageType finalMessageType = messageType;
            cookieManager.getCookieStore().removeAll();

            if (!isPasswordUpdate && finalMessageType == null) {
                Bundle bundle = new Bundle();
                bundle.putString(BundleKeys.INSTANCE.getKEY_USERNAME(), loginData.getUsername());
                bundle.putString(BundleKeys.INSTANCE.getKEY_TOKEN(), loginData.getToken());
                bundle.putString(BundleKeys.INSTANCE.getKEY_BASE_URL(), loginData.getServerUrl());
                String protocol = "";

                if (baseUrl.startsWith("http://")) {
                    protocol = "http://";
                } else if (baseUrl.startsWith("https://")) {
                    protocol = "https://";
                }

                if (!TextUtils.isEmpty(protocol)) {
                    bundle.putString(BundleKeys.INSTANCE.getKEY_ORIGINAL_PROTOCOL(), protocol);
                }

                getRouter().pushController(RouterTransaction.with(new AccountVerificationController
                        (bundle)).pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler()));
            } else {
                if (isPasswordUpdate) {
                    if (currentUser != null) {
                        userQueryDisposable = userUtils.createOrUpdateUser(null, loginData.getToken(),
                                null, null, "", Boolean.TRUE,
                                null, currentUser.getId(), null, appPreferences.getTemporaryClientCertAlias(), null)
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .subscribe(userEntity -> {
                                            if (finalMessageType != null) {
                                                ApplicationWideMessageHolder.getInstance().setMessageType(finalMessageType);
                                            }

                                            OneTimeWorkRequest pushRegistrationWork = new OneTimeWorkRequest.Builder(PushRegistrationWorker.class).build();
                                            WorkManager.getInstance().enqueue(pushRegistrationWork);

                                            getRouter().popCurrentController();
                                        }, throwable -> dispose(),
                                        this::dispose);
                    }
                } else {
                    if (finalMessageType != null) {
                        // FIXME when the user registers a new account that was setup before (aka
                        //  ApplicationWideMessageHolder.MessageType.ACCOUNT_UPDATED_NOT_ADDED)
                        //  The token is not updated in the database and therefor the account not visible/usable
                        ApplicationWideMessageHolder.getInstance().setMessageType(finalMessageType);
                    }
                    getRouter().popToRoot();

                }
            }
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
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);

        if (getActivity() != null && getResources() != null) {
            DisplayUtils.applyColorToStatusBar(getActivity(), ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
            DisplayUtils.applyColorToNavigationBar(getActivity().getWindow(), ResourcesCompat.getColor(getResources(), R.color.colorPrimary, null));
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dispose();
    }

    @Override
    protected void onDestroyView(@NonNull View view) {
        super.onDestroyView(view);
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    @Override
    public AppBarLayoutType getAppBarLayoutType() {
        return AppBarLayoutType.EMPTY;
    }
}
