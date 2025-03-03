/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2017-2019 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.filebrowser.models;

import at.bitfire.dav4jvm.Response;

public class DavResponse {
    public Response response;
    public Object data;

    public Response getResponse() {
        return this.response;
    }

    public Object getData() {
        return this.data;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof DavResponse)) {
            return false;
        }
        final DavResponse other = (DavResponse) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$response = this.getResponse();
        final Object other$response = other.getResponse();
        if (this$response == null ? other$response != null : !this$response.equals(other$response)) {
            return false;
        }
        final Object this$data = this.getData();
        final Object other$data = other.getData();

        return this$data == null ? other$data == null : this$data.equals(other$data);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof DavResponse;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $response = this.getResponse();
        result = result * PRIME + ($response == null ? 43 : $response.hashCode());
        final Object $data = this.getData();
        return result * PRIME + ($data == null ? 43 : $data.hashCode());
    }

    public String toString() {
        return "DavResponse(response=" + this.getResponse() + ", data=" + this.getData() + ")";
    }
}
