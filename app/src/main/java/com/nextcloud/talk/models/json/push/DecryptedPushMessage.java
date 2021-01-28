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

package com.nextcloud.talk.models.json.push;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonIgnore;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import org.parceler.Parcel;

@Parcel
@JsonObject
public class DecryptedPushMessage {
    @JsonField(name = "app")
    public String app;

    @JsonField(name = "type")
    public String type;

    @JsonField(name = "subject")
    public String subject;

    @JsonField(name = "id")
    public String id;

    @JsonField(name = "nid")
    public long notificationId;

    @JsonField(name = "delete")
    public boolean delete;

    @JsonField(name = "delete-all")
    public boolean deleteAll;

    @JsonIgnore
    public NotificationUser notificationUser;

    @JsonIgnore
    public String text;

    @JsonIgnore
    public long timestamp;

    public DecryptedPushMessage() {
    }

    public String getApp() {
        return this.app;
    }

    public String getType() {
        return this.type;
    }

    public String getSubject() {
        return this.subject;
    }

    public String getId() {
        return this.id;
    }

    public long getNotificationId() {
        return this.notificationId;
    }

    public boolean isDelete() {
        return this.delete;
    }

    public boolean isDeleteAll() {
        return this.deleteAll;
    }

    public NotificationUser getNotificationUser() {
        return this.notificationUser;
    }

    public String getText() {
        return this.text;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public void setType(String type) {
        this.type = type;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setNotificationId(long notificationId) {
        this.notificationId = notificationId;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
    }

    public void setDeleteAll(boolean deleteAll) {
        this.deleteAll = deleteAll;
    }

    public void setNotificationUser(NotificationUser notificationUser) {
        this.notificationUser = notificationUser;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public boolean equals(final Object o) {
        if (o == this) return true;
        if (!(o instanceof DecryptedPushMessage)) return false;
        final DecryptedPushMessage other = (DecryptedPushMessage) o;
        if (!other.canEqual((Object) this)) return false;
        final Object this$app = this.getApp();
        final Object other$app = other.getApp();
        if (this$app == null ? other$app != null : !this$app.equals(other$app)) return false;
        final Object this$type = this.getType();
        final Object other$type = other.getType();
        if (this$type == null ? other$type != null : !this$type.equals(other$type)) return false;
        final Object this$subject = this.getSubject();
        final Object other$subject = other.getSubject();
        if (this$subject == null ? other$subject != null : !this$subject.equals(other$subject)) return false;
        final Object this$id = this.getId();
        final Object other$id = other.getId();
        if (this$id == null ? other$id != null : !this$id.equals(other$id)) return false;
        if (this.getNotificationId() != other.getNotificationId()) return false;
        if (this.isDelete() != other.isDelete()) return false;
        if (this.isDeleteAll() != other.isDeleteAll()) return false;
        final Object this$notificationUser = this.getNotificationUser();
        final Object other$notificationUser = other.getNotificationUser();
        if (this$notificationUser == null ? other$notificationUser != null : !this$notificationUser.equals(other$notificationUser))
            return false;
        final Object this$text = this.getText();
        final Object other$text = other.getText();
        if (this$text == null ? other$text != null : !this$text.equals(other$text)) return false;
        if (this.getTimestamp() != other.getTimestamp()) return false;
        return true;
    }

    protected boolean canEqual(final Object other) {
        return other instanceof DecryptedPushMessage;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $app = this.getApp();
        result = result * PRIME + ($app == null ? 43 : $app.hashCode());
        final Object $type = this.getType();
        result = result * PRIME + ($type == null ? 43 : $type.hashCode());
        final Object $subject = this.getSubject();
        result = result * PRIME + ($subject == null ? 43 : $subject.hashCode());
        final Object $id = this.getId();
        result = result * PRIME + ($id == null ? 43 : $id.hashCode());
        final long $notificationId = this.getNotificationId();
        result = result * PRIME + (int) ($notificationId >>> 32 ^ $notificationId);
        result = result * PRIME + (this.isDelete() ? 79 : 97);
        result = result * PRIME + (this.isDeleteAll() ? 79 : 97);
        final Object $notificationUser = this.getNotificationUser();
        result = result * PRIME + ($notificationUser == null ? 43 : $notificationUser.hashCode());
        final Object $text = this.getText();
        result = result * PRIME + ($text == null ? 43 : $text.hashCode());
        final long $timestamp = this.getTimestamp();
        result = result * PRIME + (int) ($timestamp >>> 32 ^ $timestamp);
        return result;
    }

    public String toString() {
        return "DecryptedPushMessage(app=" + this.getApp() + ", type=" + this.getType() + ", subject=" + this.getSubject() + ", id=" + this.getId() + ", notificationId=" + this.getNotificationId() + ", delete=" + this.isDelete() + ", deleteAll=" + this.isDeleteAll() + ", notificationUser=" + this.getNotificationUser() + ", text=" + this.getText() + ", timestamp=" + this.getTimestamp() + ")";
    }
}
