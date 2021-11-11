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

import com.nextcloud.talk.models.database.UserEntity;

public class ApplicationWideCurrentRoomHolder {
    private static final ApplicationWideCurrentRoomHolder holder = new ApplicationWideCurrentRoomHolder();
    private String currentRoomId = "";
    private String currentRoomToken = "";
    private UserEntity userInRoom = new UserEntity();
    private boolean inCall = false;
    private boolean isDialing = false;
    private String session = "";

    public static ApplicationWideCurrentRoomHolder getInstance() {
        return holder;
    }

    public void clear() {
        currentRoomId = "";
        userInRoom = new UserEntity();
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

    public UserEntity getUserInRoom() {
        return userInRoom;
    }

    public void setUserInRoom(UserEntity userInRoom) {
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
}
