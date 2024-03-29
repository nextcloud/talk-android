/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.dagger.modules;

import android.content.Context;

import androidx.annotation.NonNull;
import dagger.Module;
import dagger.Provides;

@Module
public class ContextModule {
    private final Context context;

    public ContextModule(@NonNull final Context context) {
        this.context = context;
    }

    @Provides
    public Context provideContext() {
        return context;
    }
}
