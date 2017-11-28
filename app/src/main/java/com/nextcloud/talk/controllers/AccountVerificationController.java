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
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

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

    @BindView(R.id.progress_text)
    TextView progressText;

    private Disposable roomsQueryDisposable;
    private Disposable profileQueryDisposable;
    private Disposable dbQueryDisposable;

    private String baseUrl;
    private String username;
    private String token;

    public AccountVerificationController(Bundle args) {
        super(args);
        if (args != null) {
            baseUrl = args.getString(BundleKeys.KEY_BASE_URL);
            username = args.getString(BundleKeys.KEY_USERNAME);
            token = args.getString(BundleKeys.KEY_TOKEN);
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

        String credentials = ApiHelper.getCredentials(username, token);

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
                                            baseUrl, displayName, null, true)
                                            .subscribeOn(Schedulers.newThread())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(userEntity -> {
                                                        progressText.setText(progressText.getText().toString()
                                                                + "\n" +
                                                                getResources().getString(
                                                                        R.string.nc_display_name_stored));

                                                        new JobRequest.Builder(PushRegistrationJob.TAG).
                                                                setUpdateCurrent(true).startNow().build().schedule();

                                                        userUtils.disableAllUsersWithoutId(userEntity.getId());

                                                        if (userUtils.getUsers().size() == 1) {
                                                            getRouter().setRoot(RouterTransaction.with(new
                                                                    BottomNavigationController(R.menu.menu_navigation))
                                                                    .pushChangeHandler(new HorizontalChangeHandler())
                                                                    .popChangeHandler(new HorizontalChangeHandler()));
                                                        } else {
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
    }

}
