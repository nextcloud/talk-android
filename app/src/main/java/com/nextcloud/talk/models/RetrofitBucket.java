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
package com.nextcloud.talk.models;

import org.parceler.Parcel;

import java.util.Map;

@Parcel
public class RetrofitBucket {
    public String url;
    public Map<String, String> queryMap;

    public RetrofitBucket() {
    }

    public String getUrl() {
        return this.url;
    }

    public Map<String, String> getQueryMap() {
        return this.queryMap;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public void setQueryMap(Map<String, String> queryMap) {
        this.queryMap = queryMap;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof RetrofitBucket)) {
            return false;
        }
        final RetrofitBucket other = (RetrofitBucket) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$url = this.getUrl();
        final Object other$url = other.getUrl();
        if (this$url == null ? other$url != null : !this$url.equals(other$url)) {
            return false;
        }
        final Object this$queryMap = this.getQueryMap();
        final Object other$queryMap = other.getQueryMap();
        if (this$queryMap == null ? other$queryMap != null : !this$queryMap.equals(other$queryMap)) {
            return false;
        }
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof RetrofitBucket;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $url = this.getUrl();
        result = result * PRIME + ($url == null ? 43 : $url.hashCode());
        final Object $queryMap = this.getQueryMap();
        result = result * PRIME + ($queryMap == null ? 43 : $queryMap.hashCode());
        return result;
    }

    public String toString() {
        return "RetrofitBucket(url=" + this.getUrl() + ", queryMap=" + this.getQueryMap() + ")";
    }
}
