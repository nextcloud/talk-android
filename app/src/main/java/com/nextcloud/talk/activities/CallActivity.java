/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.activities;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import com.bluelinelabs.conductor.Conductor;
import com.bluelinelabs.conductor.Router;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.SimpleSwapChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.CallController;
import com.nextcloud.talk.utils.bundle.BundleBuilder;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.ButterKnife;

@AutoInjector(NextcloudTalkApplication.class)
public class CallActivity extends AppCompatActivity {
    private static final String TAG = "CallActivity";

    @BindView(R.id.controller_container)
    ViewGroup container;

    private Router router;

    private String roomToken;
    private String userDisplayName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(getSystemUiVisibility());

        setContentView(R.layout.activity_call);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        ButterKnife.bind(this);

        roomToken = getIntent().getExtras().getString("roomToken", "");
        userDisplayName = getIntent().getExtras().getString("userDisplayName", "");

        router = Conductor.attachRouter(this, container, savedInstanceState);

        if (!router.hasRootController()) {
            BundleBuilder bundleBuilder = new BundleBuilder(new Bundle());
            bundleBuilder.putString("roomToken", roomToken);
            bundleBuilder.putString("userDisplayName", userDisplayName);

            router.setRoot(RouterTransaction.with(new CallController(bundleBuilder.build()))
            .popChangeHandler(new SimpleSwapChangeHandler())
            .pushChangeHandler((new SimpleSwapChangeHandler())));
        }
    }

    @Override
    public void onBackPressed() {
        if (!router.handleBack()) {
            super.onBackPressed();
        }
    }

    private static int getSystemUiVisibility() {
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN;
        flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        return flags;
    }
}
