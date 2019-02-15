/**
 * Nextcloud Talk application
 *
 * @author BlueLine Labs, Inc.
 * Copyright (C) 2016 BlueLine Labs, Inc.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nextcloud.talk.controllers.base;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import autodagger.AutoInjector;
import com.bluelinelabs.conductor.Controller;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.AccountVerificationController;
import com.nextcloud.talk.controllers.ServerSelectionController;
import com.nextcloud.talk.controllers.SwitchAccountController;
import com.nextcloud.talk.controllers.WebViewLoginController;
import com.nextcloud.talk.controllers.base.providers.ActionBarProvider;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

@AutoInjector(NextcloudTalkApplication.class)
public abstract class BaseController extends ButterKnifeController {

    private static final String TAG = "BaseController";
    @Inject
    AppPreferences appPreferences;
    @Inject
    Context context;

    protected BaseController() {
        cleanTempCertPreference();
    }

    protected BaseController(Bundle args) {
        super(args);
        cleanTempCertPreference();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getRouter().popCurrentController();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.getIsKeyboardIncognito()) {
            disableKeyboardPersonalisedLearning((ViewGroup) view);
        }
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
        super.onAttach(view);

        setTitle();
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(getParentController() != null || getRouter().getBackstackSize() > 1);
        }
    }

    @Override
    protected void onDetach(@NonNull View view) {
        super.onDetach(view);
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
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

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void disableKeyboardPersonalisedLearning(final ViewGroup viewGroup) {
        View view;
        EditText editText;

        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            view = viewGroup.getChildAt(i);
            if (view instanceof EditText) {
                editText = (EditText) view;
                editText.setImeOptions(editText.getImeOptions() | EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
            } else if (view instanceof ViewGroup) {
                disableKeyboardPersonalisedLearning((ViewGroup) view);
            }
        }
    }
}
