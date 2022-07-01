/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.models.database;

import android.os.Parcelable;

import java.io.Serializable;

import io.requery.Entity;
import io.requery.Generated;
import io.requery.Key;
import io.requery.Persistable;

/**
 * Legacy user entity, please migrate to {@link com.nextcloud.talk.data.user.model.User}.
 */
@Deprecated
@Entity
public interface User extends Parcelable, Persistable, Serializable {
    String TAG = "UserEntity";

    @Key
    @Generated
    long getId();

    String getUserId();

    String getUsername();

    String getBaseUrl();

    String getToken();

    String getDisplayName();

    String getPushConfigurationState();

    String getCapabilities();

    String getClientCertificate();

    String getExternalSignalingServer();

    boolean getCurrent();

    boolean getScheduledForDeletion();
}
