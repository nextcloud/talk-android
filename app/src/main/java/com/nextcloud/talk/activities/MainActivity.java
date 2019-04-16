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
package com.nextcloud.talk.activities;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import androidx.annotation.RequiresApi;
import androidx.appcompat.widget.Toolbar;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.bluelinelabs.conductor.Conductor;
import com.bluelinelabs.conductor.Router;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.*;
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider;
import com.nextcloud.talk.utils.SecurityUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import io.requery.Persistable;
import io.requery.android.sqlcipher.SqlCipherDatabaseSource;
import io.requery.reactivex.ReactiveEntityStore;

import javax.inject.Inject;

@AutoInjector(NextcloudTalkApplication.class)
public final class MainActivity extends BaseActivity implements ActionBarProvider {
    private static final String TAG = "MainActivity";

    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.controller_container)
    ViewGroup container;

    @Inject
    UserUtils userUtils;
    @Inject
    ReactiveEntityStore<Persistable> dataStore;
    @Inject
    SqlCipherDatabaseSource sqlCipherDatabaseSource;

    private Router router;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        ButterKnife.bind(this);

        setSupportActionBar(toolbar);

        router = Conductor.attachRouter(this, container, savedInstanceState);

        boolean hasDb = true;

        try {
            sqlCipherDatabaseSource.getWritableDatabase();
        } catch (Exception exception) {
            hasDb = false;
        }

        if (getIntent().hasExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            if (!router.hasRootController()) {
                router.setRoot(RouterTransaction.with(new ConversationsListController())
                        .pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler()));
            }
            onNewIntent(getIntent());
        } else if (!router.hasRootController()) {
            if (hasDb) {
                if (userUtils.anyUserExists()) {
                    router.setRoot(RouterTransaction.with(new ConversationsListController())
                            .pushChangeHandler(new HorizontalChangeHandler())
                            .popChangeHandler(new HorizontalChangeHandler()));
                } else {
                    router.setRoot(RouterTransaction.with(new ServerSelectionController())
                            .pushChangeHandler(new HorizontalChangeHandler())
                            .popChangeHandler(new HorizontalChangeHandler()));
                }
            } else {
                router.setRoot(RouterTransaction.with(new ServerSelectionController())
                        .pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler()));

            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            checkIfWeAreSecure();
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.M)
    public void checkIfWeAreSecure() {
        KeyguardManager keyguardManager = (KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguardManager != null && keyguardManager.isKeyguardSecure() && appPreferences.getIsScreenLocked()) {
            if (!SecurityUtils.checkIfWeAreAuthenticated(appPreferences.getScreenLockTimeout())) {
                if (router != null && router.getControllerWithTag(LockedController.TAG) == null) {
                    router.pushController(RouterTransaction.with(new LockedController())
                            .pushChangeHandler(new VerticalChangeHandler())
                            .popChangeHandler(new VerticalChangeHandler())
                            .tag(LockedController.TAG));
                }
            }
        }
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        if (intent.hasExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            if (intent.getBooleanExtra(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL, false)) {
                router.pushController(RouterTransaction.with(new CallNotificationController(intent.getExtras()))
                        .pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler()));
            } else {
                router.pushController(RouterTransaction.with(new ChatController(intent.getExtras()))
                        .pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler()));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (router.getControllerWithTag(LockedController.TAG) != null) {
            return;
        }

        if (!router.handleBack()) {
            super.onBackPressed();
        }
    }
}
