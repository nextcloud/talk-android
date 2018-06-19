/**
 * Nextcloud Talk application
 *
 * @author BlueLine Labs, Inc.
 * Copyright (C) 2016 BlueLine Labs, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nextcloud.talk.controllers.base;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.util.Log;
import android.view.View;

import com.bluelinelabs.conductor.Controller;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.AccountVerificationController;
import com.nextcloud.talk.controllers.MagicBottomNavigationController;
import com.nextcloud.talk.controllers.ServerSelectionController;
import com.nextcloud.talk.controllers.SwitchAccountController;
import com.nextcloud.talk.controllers.WebViewLoginController;
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;

@AutoInjector(NextcloudTalkApplication.class)
public abstract class BaseController extends RefWatchingController {

    private static final String TAG = "BaseController";
    @Inject
    AppPreferences appPreferences;

    protected BaseController() {
        cleanTempCertPreference();
    }

    protected BaseController(Bundle args) {
        super(args);
        cleanTempCertPreference();
    }

    private void cleanTempCertPreference() {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        List<String> temporaryClassNames = new ArrayList<>();
        temporaryClassNames.add(ServerSelectionController.class.getName());
        temporaryClassNames.add(AccountVerificationController.class.getName());
        temporaryClassNames.add(WebViewLoginController.class.getName());
        temporaryClassNames.add(SwitchAccountController.class.getName());

        if (!temporaryClassNames.contains(getClass().getName())) {
            appPreferences.removeTemporaryClientCertAlias();
        }

    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
    }

    // Note: This is just a quick demo of how an ActionBar *can* be accessed, not necessarily how it *should*
    // be accessed. In a production app, this would use Dagger instead.
    protected ActionBar getActionBar() {
        ActionBarProvider actionBarProvider = null;
        try {
            actionBarProvider = ((ActionBarProvider) getActivity());
        } catch (Exception exception) {
            Log.d(TAG, "Failed to fetch the action bar provider");
        }
        return actionBarProvider != null ? actionBarProvider.getSupportActionBar() : null;
    }

    @Override
    protected void onAttach(@NonNull View view) {
        setTitle();
        if (!MagicBottomNavigationController.class.getName().equals(getClass().getName()) && getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
        }

        super.onAttach(view);
    }

    protected void setTitle() {
        Controller parentController = getParentController();
        while (parentController != null) {
            if (parentController instanceof BaseController && ((BaseController) parentController).getTitle() != null) {
                return;
            }
            parentController = parentController.getParentController();
        }

        String title = getTitle();
        ActionBar actionBar = getActionBar();
        if (title != null && actionBar != null) {
            actionBar.setTitle(title);
        }
    }

    protected String getTitle() {
        return null;
    }
}
