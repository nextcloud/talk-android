/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.models;

import org.parceler.Parcel;

@Parcel
public class LoginData {
    String serverUrl;
    String username;
    String token;

    public LoginData() {
    }

    public String getServerUrl() {
        return this.serverUrl;
    }

    public String getUsername() {
        return this.username;
    }

    public String getToken() {
        return this.token;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof LoginData)) {
            return false;
        }
        final LoginData other = (LoginData) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$serverUrl = this.getServerUrl();
        final Object other$serverUrl = other.getServerUrl();
        if (this$serverUrl == null ? other$serverUrl != null : !this$serverUrl.equals(other$serverUrl)) {
            return false;
        }
        final Object this$username = this.getUsername();
        final Object other$username = other.getUsername();
        if (this$username == null ? other$username != null : !this$username.equals(other$username)) {
            return false;
        }
        final Object this$token = this.getToken();
        final Object other$token = other.getToken();
        if (this$token == null ? other$token != null : !this$token.equals(other$token)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof LoginData;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $serverUrl = this.getServerUrl();
        result = result * PRIME + ($serverUrl == null ? 43 : $serverUrl.hashCode());
        final Object $username = this.getUsername();
        result = result * PRIME + ($username == null ? 43 : $username.hashCode());
        final Object $token = this.getToken();
        result = result * PRIME + ($token == null ? 43 : $token.hashCode());
        return result;
    }

    public String toString() {
        return "LoginData(serverUrl=" + this.getServerUrl() + ", username=" + this.getUsername() + ", token=" + this.getToken() + ")";
    }
}
