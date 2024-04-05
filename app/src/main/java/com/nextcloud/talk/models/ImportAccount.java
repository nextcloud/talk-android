/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models;

import androidx.annotation.Nullable;

public class ImportAccount {
    public String username;
    @Nullable
    public String token;
    public String baseUrl;

    public ImportAccount(String username, @Nullable String token, String baseUrl) {
        this.username = username;
        this.token = token;
        this.baseUrl = baseUrl;
    }

    public String getUsername() {
        return this.username;
    }

    @Nullable
    public String getToken() {
        return this.token;
    }

    public String getBaseUrl() {
        return this.baseUrl;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setToken(@Nullable String token) {
        this.token = token;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof ImportAccount)) {
            return false;
        }
        final ImportAccount other = (ImportAccount) o;
        if (!other.canEqual((Object) this)) {
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
        final Object this$baseUrl = this.getBaseUrl();
        final Object other$baseUrl = other.getBaseUrl();

        return this$baseUrl == null ? other$baseUrl == null : this$baseUrl.equals(other$baseUrl);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof ImportAccount;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $username = this.getUsername();
        result = result * PRIME + ($username == null ? 43 : $username.hashCode());
        final Object $token = this.getToken();
        result = result * PRIME + ($token == null ? 43 : $token.hashCode());
        final Object $baseUrl = this.getBaseUrl();
        return result * PRIME + ($baseUrl == null ? 43 : $baseUrl.hashCode());
    }

    public String toString() {
        return "ImportAccount(username=" + this.getUsername() + ", token=" + this.getToken() + ", baseUrl=" + this.getBaseUrl() + ")";
    }
}
