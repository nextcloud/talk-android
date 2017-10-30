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

package com.nextcloud.talk.controllers;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.RefWatchingController;

import org.webrtc.SurfaceViewRenderer;

import autodagger.AutoInjector;
import butterknife.BindView;

@AutoInjector(NextcloudTalkApplication.class)
public class CallController extends RefWatchingController {
    private static final String TAG = "CallController";

    @BindView(R.id.fullscreen_video_view)
    SurfaceViewRenderer fullScreenVideo;

    @BindView(R.id.pip_video_view)
    SurfaceViewRenderer pipVideoView;

    private String roomToken;
    private String userDisplayName;

    public CallController(Bundle args) {
        super(args);
        this.roomToken = args.getString("roomToken", "");
        this.userDisplayName = args.getString("userDisplayName");

    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_call, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
    }

}
