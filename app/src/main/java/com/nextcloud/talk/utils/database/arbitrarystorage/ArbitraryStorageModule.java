/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.database.arbitrarystorage;

import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.arbitrarystorage.ArbitraryStorageManager;
import com.nextcloud.talk.dagger.modules.DatabaseModule;
import com.nextcloud.talk.data.storage.ArbitraryStoragesRepository;

import javax.inject.Inject;

import autodagger.AutoInjector;
import dagger.Module;
import dagger.Provides;

@Module(includes = DatabaseModule.class)
@AutoInjector(NextcloudTalkApplication.class)
public class ArbitraryStorageModule {

    @Inject
    public ArbitraryStorageModule() {
    }

    @Provides
    public ArbitraryStorageManager provideArbitraryStorageManager(ArbitraryStoragesRepository repository) {
        return new ArbitraryStorageManager(repository);
    }
}
