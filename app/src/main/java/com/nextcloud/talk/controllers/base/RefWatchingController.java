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

import com.bluelinelabs.conductor.ControllerChangeHandler;
import com.bluelinelabs.conductor.ControllerChangeType;
import com.nextcloud.talk.application.NextcloudTalkApplication;

public abstract class RefWatchingController extends ButterKnifeController {

    private boolean hasExited;

    protected RefWatchingController() {
    }

    protected RefWatchingController(Bundle args) {
        super(args);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (hasExited) {
            NextcloudTalkApplication.refWatcher.watch(this);
        }
    }

    @Override
    protected void onChangeEnded(@NonNull ControllerChangeHandler changeHandler, @NonNull ControllerChangeType changeType) {
        super.onChangeEnded(changeHandler, changeType);

        hasExited = !changeType.isEnter;
        if (isDestroyed()) {
            NextcloudTalkApplication.refWatcher.watch(this);
        }
    }
}
