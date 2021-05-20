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

package com.nextcloud.talk.models.json.signaling.settings;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import java.util.List;

@JsonObject
public class IceServer {
    @Deprecated
    @JsonField(name = "url")
    String url;

    @JsonField(name = "urls")
    List<String> urls;

    @JsonField(name = "username")
    String username;

    @JsonField(name = "credential")
    String credential;

    @Deprecated
    public String getUrl() {
        return this.url;
    }

    public List<String> getUrls() {
        return this.urls;
    }

    public String getUsername() {
        return this.username;
    }

    public String getCredential() {
        return this.credential;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setUrls(List<String> urls) {
        this.urls = urls;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof IceServer)) {
            return false;
        }
        final IceServer other = (IceServer) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$url = this.getUrl();
        final Object other$url = other.getUrl();
        if (this$url == null ? other$url != null : !this$url.equals(other$url)) {
            return false;
        }
        final Object this$urls = this.getUrls();
        final Object other$urls = other.getUrls();
        if (this$urls == null ? other$urls != null : !this$urls.equals(other$urls)) {
            return false;
        }
        final Object this$username = this.getUsername();
        final Object other$username = other.getUsername();
        if (this$username == null ? other$username != null : !this$username.equals(other$username)) {
            return false;
        }
        final Object this$credential = this.getCredential();
        final Object other$credential = other.getCredential();

        return this$credential == null ? other$credential == null : this$credential.equals(other$credential);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof IceServer;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $url = this.getUrl();
        result = result * PRIME + ($url == null ? 43 : $url.hashCode());
        final Object $urls = this.getUrls();
        result = result * PRIME + ($urls == null ? 43 : $urls.hashCode());
        final Object $username = this.getUsername();
        result = result * PRIME + ($username == null ? 43 : $username.hashCode());
        final Object $credential = this.getCredential();
        result = result * PRIME + ($credential == null ? 43 : $credential.hashCode());
        return result;
    }

    public String toString() {
        return "IceServer(url=" + this.getUrl() + ", urls=" + this.getUrls() + ", username=" + this.getUsername() + ", credential=" + this.getCredential() + ")";
    }
}
