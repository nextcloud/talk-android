/*
 * Nextcloud Talk application
 *
 * @author Tobias Kaminsky
 * Copyright (C) 2021 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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

package com.moyn.talk.components.filebrowser.controllers;

import android.content.Intent;
import android.os.Bundle;

import com.moyn.talk.controllers.ProfileController;

import androidx.annotation.Nullable;

public class BrowserForAvatarController extends BrowserController {
    private ProfileController controller;

    public BrowserForAvatarController(Bundle args) {
        super(args);
    }

    public BrowserForAvatarController(Bundle args, ProfileController controller) {
        super(args);

        this.controller = controller;
    }

    @Override
    void onFileSelectionDone() {
        controller.handleAvatar(selectedPaths.iterator().next());

        getRouter().popCurrentController();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean shouldOnlySelectOneImageFile() {
        return true;
    }
}
