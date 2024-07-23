/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.dagger.modules;

import dagger.Module;
import dagger.Provides;

import org.greenrobot.eventbus.EventBus;

import javax.inject.Singleton;

@Module
public class BusModule {

    @Provides
    @Singleton
    public EventBus provideEventBus() {
        return EventBus.getDefault();
    }
}