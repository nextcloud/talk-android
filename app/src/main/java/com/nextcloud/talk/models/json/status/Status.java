/*
 *
 *   Nextcloud Talk application
 *
 *   @author Tim Krüger
 *   Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
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
package com.nextcloud.talk.models.json.status;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

import java.util.Objects;

@Parcel
@JsonObject
public class Status {

    @JsonField(name = "userId")
    public String userId;

    @JsonField(name = "message")
    public String message;

    // TODO: Change to enum
    @JsonField(name = "messageId")
    public String messageId;

    @JsonField(name = "messageIsPredefined")
    public boolean messageIsPredefined;

    @JsonField(name = "icon")
    public String icon;

    @JsonField(name = "clearAt")
    public long clearAt;

    // TODO: Change to enum
    @JsonField(name = "status")
    public String status;

    @JsonField(name = "statusIsUserDefined")
    public boolean statusIsUserDefined;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public boolean isMessageIsPredefined() {
        return messageIsPredefined;
    }

    public void setMessageIsPredefined(boolean messageIsPredefined) {
        this.messageIsPredefined = messageIsPredefined;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public long getClearAt() {
        return clearAt;
    }

    public void setClearAt(long clearAt) {
        this.clearAt = clearAt;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isStatusIsUserDefined() {
        return statusIsUserDefined;
    }

    public void setStatusIsUserDefined(boolean statusIsUserDefined) {
        this.statusIsUserDefined = statusIsUserDefined;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Status status1 = (Status) o;
        return messageIsPredefined == status1.messageIsPredefined &&
            clearAt == status1.clearAt &&
            statusIsUserDefined == status1.statusIsUserDefined &&
            Objects.equals(userId, status1.userId) && Objects.equals(message, status1.message) &&
            Objects.equals(messageId, status1.messageId) && Objects.equals(icon, status1.icon) &&
            Objects.equals(status, status1.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, message, messageId, messageIsPredefined, icon, clearAt, status, statusIsUserDefined);
    }

    @Override
    public String toString() {
        return "Status{" +
            "userId='" + userId + '\'' +
            ", message='" + message + '\'' +
            ", messageId='" + messageId + '\'' +
            ", messageIsPredefined=" + messageIsPredefined +
            ", icon='" + icon + '\'' +
            ", clearAt=" + clearAt +
            ", status='" + status + '\'' +
            ", statusIsUserDefined=" + statusIsUserDefined +
            '}';
    }
}
