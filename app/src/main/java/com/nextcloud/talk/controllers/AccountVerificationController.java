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
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.evernote.android.job.JobRequest;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.jobs.PushRegistrationJob;
import com.nextcloud.talk.utils.ApplicationWideMessageHolder;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import java.net.CookieManager;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import io.reactivex.CompletableObserver;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


@AutoInjector(NextcloudTalkApplication.class)
public class AccountVerificationController extends BaseController {

    public static final String TAG = "AccountVerificationController";

    @Inject
    NcApi ncApi;

    @Inject
    UserUtils userUtils;

    @Inject
    CookieManager cookieManager;

    @BindView(R.id.progress_text)
    TextView progressText;

    private Disposable roomsQueryDisposable;
    private Disposable profileQueryDisposable;
    private Disposable dbQueryDisposable;
    private Disposable statusQueryDisposable;

    private String baseUrl;
    private String username;
    private String token;
    private boolean isAccountImport;
    private String originalProtocol;

    public AccountVerificationController(Bundle args) {
        super(args);
        if (args != null) {
            baseUrl = args.getString(BundleKeys.KEY_BASE_URL);
            username = args.getString(BundleKeys.KEY_USERNAME);
            token = args.getString(BundleKeys.KEY_TOKEN);
            if (args.containsKey(BundleKeys.KEY_IS_ACCOUNT_IMPORT)) {
                isAccountImport = true;
            }
            if (args.containsKey(BundleKeys.KEY_ORIGINAL_PROTOCOL)) {
                originalProtocol = args.getString(BundleKeys.KEY_ORIGINAL_PROTOCOL);
            }
        }
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_account_verification, container, false);
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

        dispose(null);

        if (isAccountImport && !baseUrl.startsWith("http://") && !baseUrl.startsWith("https://") || (!TextUtils
                .isEmpty(originalProtocol) && !baseUrl.startsWith(originalProtocol))) {
            determineBaseUrlProtocol(true);
        } else {
            checkEverything();
        }

    }

    private void determineBaseUrlProtocol(boolean checkForcedHttps) {
        cookieManager.getCookieStore().removeAll();

        String queryUrl;

        baseUrl = baseUrl.replace("http://", "").replace("https://", "");

        if (checkForcedHttps) {
            queryUrl = "https://" + baseUrl + ApiHelper.getUrlPostfixForStatus();
        } else {
            queryUrl = "http://" + baseUrl + ApiHelper.getUrlPostfixForStatus();
        }

        statusQueryDisposable = ncApi.getServerStatus(queryUrl)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(status -> {
                    if (checkForcedHttps) {
                        baseUrl = "https://" + baseUrl;
                    } else {
                        baseUrl = "http://" + baseUrl;
                    }

                    checkEverything();
                }, throwable -> {
                    if (checkForcedHttps) {
                        determineBaseUrlProtocol(false);
                    } else {
                        abortVerification();
                    }
                }, () -> {
                    statusQueryDisposable.dispose();
                });
    }

    private void checkEverything() {
        String credentials = ApiHelper.getCredentials(username, token);
        cookieManager.getCookieStore().removeAll();

        roomsQueryDisposable = ncApi.getRooms(credentials, ApiHelper.getUrlForGetRooms(baseUrl))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(roomsOverall -> {
                    progressText.setText(String.format(getResources().getString(
                            R.string.nc_nextcloud_talk_app_installed), getResources().getString(R.string.nc_app_name)));

                    profileQueryDisposable = ncApi.getUserProfile(credentials,
                            ApiHelper.getUrlForUserProfile(baseUrl))
                            .subscribeOn(Schedulers.newThread())
                            .observeOn(AndroidSchedulers.mainThread())
                            .subscribe(userProfileOverall -> {
                                progressText.setText(progressText.getText().toString() + "\n" +
                                        getResources().getString(R.string.nc_display_name_fetched));

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

                                if (!TextUtils.isEmpty(displayName)) {
                                    dbQueryDisposable = userUtils.createOrUpdateUser(username, token,
                                            baseUrl, displayName, null, true,
                                            userProfileOverall.getOcs().getData().getUserId(), null)
                                            .subscribeOn(Schedulers.newThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(userEntity -> {
                                                        progressText.setText(progressText.getText().toString()
                                                                + "\n" +
                                                                getResources().getString(
                                                                        R.string.nc_display_name_stored));

                                                        new JobRequest.Builder(PushRegistrationJob.TAG).
                                                                setUpdateCurrent(true).startNow().build().schedule();

                                                        cookieManager.getCookieStore().removeAll();
                                                        userUtils.disableAllUsersWithoutId(userEntity.getId());

                                                        if (userUtils.getUsers().size() == 1) {
                                                            getRouter().setRoot(RouterTransaction.with(new
                                                                    MagicBottomNavigationController())
                                                                    .pushChangeHandler(new HorizontalChangeHandler())
                                                                    .popChangeHandler(new HorizontalChangeHandler()));
                                                        } else {
                                                            if (isAccountImport) {
                                                                ApplicationWideMessageHolder.getInstance().setMessageType(
                                                                        ApplicationWideMessageHolder.MessageType.ACCOUNT_WAS_IMPORTED);
                                                            }
                                                            getRouter().popToRoot();
                                                        }
                                                    },
                                                    throwable -> {
                                                        progressText.setText(progressText.getText().toString() +
                                                                "\n" +
                                                                getResources().getString(
                                                                        R.string.nc_display_name_not_stored));
                                                        abortVerification();
                                                    }, () -> dispose(dbQueryDisposable));

                                } else {
                                    progressText.setText(progressText.getText().toString() + "\n" +
                                            getResources().getString(R.string.nc_display_name_not_fetched));
                                    abortVerification();
                                }
                            }, throwable -> {
                                progressText.setText(progressText.getText().toString()
                                        + "\n" + getResources().getString(
                                        R.string.nc_display_name_not_fetched));
                                abortVerification();
                            }, () -> dispose(profileQueryDisposable));

                }, throwable -> {
                    progressText.setText(String.format(getResources().getString(
                            R.string.nc_nextcloud_talk_app_not_installed), getResources().getString(R.string.nc_app_name)));
                    ApplicationWideMessageHolder.getInstance().setMessageType(
                            ApplicationWideMessageHolder.MessageType.SERVER_WITHOUT_TALK);

                    abortVerification();
                }, () -> dispose(roomsQueryDisposable));
    }

    private void dispose(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        } else if (disposable == null) {
            if (roomsQueryDisposable != null && !roomsQueryDisposable.isDisposed()) {
                roomsQueryDisposable.dispose();
                roomsQueryDisposable = null;
            }

            if (profileQueryDisposable != null && !profileQueryDisposable.isDisposed()) {
                profileQueryDisposable.dispose();
                profileQueryDisposable = null;
            }

            if (dbQueryDisposable != null && !dbQueryDisposable.isDisposed()) {
                dbQueryDisposable.dispose();
                dbQueryDisposable = null;
            }

            if (statusQueryDisposable != null && !statusQueryDisposable.isDisposed()) {
                statusQueryDisposable.dispose();
                statusQueryDisposable = null;
            }
        }

        disposable = null;
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
        dispose(null);
    }

    private void abortVerification() {
        dispose(null);

        if (!isAccountImport) {
            userUtils.deleteUser(username, baseUrl).subscribe(new CompletableObserver() {
                @Override
                public void onSubscribe(Disposable d) {

                }

                @Override
                public void onComplete() {
                    new Handler().postDelayed(() -> getRouter().popToRoot(), 7500);
                }

                @Override
                public void onError(Throwable e) {

                }
            });
        } else {
            ApplicationWideMessageHolder.getInstance().setMessageType(
                    ApplicationWideMessageHolder.MessageType.FAILED_TO_IMPORT_ACCOUNT);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (getRouter().hasRootController()) {
                        getRouter().popToRoot();
                    } else {
                        if (userUtils.anyUserExists()) {
                            getRouter().setRoot(RouterTransaction.with(new MagicBottomNavigationController())
                                    .pushChangeHandler(new HorizontalChangeHandler())
                                    .popChangeHandler(new HorizontalChangeHandler()));
                        } else {
                            getRouter().setRoot(RouterTransaction.with(new ServerSelectionController())
                                    .pushChangeHandler(new HorizontalChangeHandler())
                                    .popChangeHandler(new HorizontalChangeHandler()));
                        }
                    }
                }
            }, 7500);
        }
    }

}
