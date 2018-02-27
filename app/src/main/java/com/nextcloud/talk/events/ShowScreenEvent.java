/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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

package com.nextcloud.talk.events;

import android.os.Bundle;
import android.support.annotation.Nullable;

import lombok.Data;

@Data
public class ShowScreenEvent {
    public enum ScreenType {
        CONTACTS_SCREEN
    }

    @Nullable private final Bundle bundle;
    private final ScreenType screenType;

    public ShowScreenEvent(ScreenType screenType, @Nullable Bundle bundle) {
        this.bundle = bundle;
        this.screenType = screenType;
    }
}
