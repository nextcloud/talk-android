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

package com.nextcloud.talk.utils.singletons;

import android.util.Log;

import com.nextcloud.talk.data.user.model.User;

public class ApplicationWideCurrentRoomHolder {

    public static final String TAG = "ApplicationWideCurrentRoomHolder";
    private static final ApplicationWideCurrentRoomHolder holder = new ApplicationWideCurrentRoomHolder();
    private String currentRoomId = "";
    private String currentRoomToken = "";
    private User userInRoom = new User();
    private boolean inCall = false;
    private boolean isDialing = false;
    private String session = "";

    private Long callStartTime = null;

    public static ApplicationWideCurrentRoomHolder getInstance() {
        return holder;
    }

    public void clear() {
        Log.d(TAG, "ApplicationWideCurrentRoomHolder was cleared");
        currentRoomId = "";
        userInRoom = new User();
        inCall = false;
        isDialing = false;
        currentRoomToken = "";
        session = "";
    }

    public String getCurrentRoomToken() {
        return currentRoomToken;
    }

    public void setCurrentRoomToken(String currentRoomToken) {
        this.currentRoomToken = currentRoomToken;
    }

    public String getCurrentRoomId() {
        return currentRoomId;
    }

    public void setCurrentRoomId(String currentRoomId) {
        this.currentRoomId = currentRoomId;
    }

    public User getUserInRoom() {
        return userInRoom;
    }

    public void setUserInRoom(User userInRoom) {
        this.userInRoom = userInRoom;
    }

    public boolean isInCall() {
        return inCall;
    }

    public void setInCall(boolean inCall) {
        this.inCall = inCall;
    }

    public boolean isDialing() {
        return isDialing;
    }

    public void setDialing(boolean dialing) {
        isDialing = dialing;
    }

    public String getSession() {
        return session;
    }

    public void setSession(String session) {
        this.session = session;
    }

    public Long getCallStartTime() {
        return callStartTime;
    }

    public void setCallStartTime(Long callStartTime) {
        this.callStartTime = callStartTime;
    }
}
