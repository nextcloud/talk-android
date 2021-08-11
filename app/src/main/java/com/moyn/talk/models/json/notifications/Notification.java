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

package com.moyn.talk.models.json.notifications;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import com.moyn.talk.models.json.converters.LoganSquareJodaTimeConverter;

import org.joda.time.DateTime;
import org.parceler.Parcel;

import java.util.HashMap;
import java.util.List;

@Parcel
@JsonObject
public class Notification {
    @JsonField(name = "icon")
    public String icon;
    @JsonField(name = "notification_id")
    int notificationId;
    @JsonField(name = "app")
    String app;
    @JsonField(name = "user")
    String user;
    @JsonField(name = "datetime", typeConverter = LoganSquareJodaTimeConverter.class)
    DateTime datetime;
    @JsonField(name = "object_type")
    String objectType;
    @JsonField(name = "object_id")
    String objectId;
    @JsonField(name = "subject")
    String subject;
    @JsonField(name = "subjectRich")
    String subjectRich;
    @JsonField(name = "subjectRichParameters")
    HashMap<String, HashMap<String, String>> subjectRichParameters;
    @JsonField(name = "message")
    String message;
    @JsonField(name = "messageRich")
    String messageRich;
    @JsonField(name = "messageRichParameters")
    HashMap<String, HashMap<String, String>> messageRichParameters;
    @JsonField(name = "link")
    String link;
    @JsonField(name = "actions")
    List<NotificationAction> actions;

    public String getIcon() {
        return this.icon;
    }

    public int getNotificationId() {
        return this.notificationId;
    }

    public String getApp() {
        return this.app;
    }

    public String getUser() {
        return this.user;
    }

    public DateTime getDatetime() {
        return this.datetime;
    }

    public String getObjectType() {
        return this.objectType;
    }

    public String getObjectId() {
        return this.objectId;
    }

    public String getSubject() {
        return this.subject;
    }

    public String getSubjectRich() {
        return this.subjectRich;
    }

    public HashMap<String, HashMap<String, String>> getSubjectRichParameters() {
        return this.subjectRichParameters;
    }

    public String getMessage() {
        return this.message;
    }

    public String getMessageRich() {
        return this.messageRich;
    }

    public HashMap<String, HashMap<String, String>> getMessageRichParameters() {
        return this.messageRichParameters;
    }

    public String getLink() {
        return this.link;
    }

    public List<NotificationAction> getActions() {
        return this.actions;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public void setNotificationId(int notificationId) {
        this.notificationId = notificationId;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setDatetime(DateTime datetime) {
        this.datetime = datetime;
    }

    public void setObjectType(String objectType) {
        this.objectType = objectType;
    }

    public void setObjectId(String objectId) {
        this.objectId = objectId;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public void setSubjectRich(String subjectRich) {
        this.subjectRich = subjectRich;
    }

    public void setSubjectRichParameters(HashMap<String, HashMap<String, String>> subjectRichParameters) {
        this.subjectRichParameters = subjectRichParameters;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setMessageRich(String messageRich) {
        this.messageRich = messageRich;
    }

    public void setMessageRichParameters(HashMap<String, HashMap<String, String>> messageRichParameters) {
        this.messageRichParameters = messageRichParameters;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public void setActions(List<NotificationAction> actions) {
        this.actions = actions;
    }

    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof Notification)) {
            return false;
        }
        final Notification other = (Notification) o;
        if (!other.canEqual((Object) this)) {
            return false;
        }
        final Object this$icon = this.getIcon();
        final Object other$icon = other.getIcon();
        if (this$icon == null ? other$icon != null : !this$icon.equals(other$icon)) {
            return false;
        }
        if (this.getNotificationId() != other.getNotificationId()) {
            return false;
        }
        final Object this$app = this.getApp();
        final Object other$app = other.getApp();
        if (this$app == null ? other$app != null : !this$app.equals(other$app)) {
            return false;
        }
        final Object this$user = this.getUser();
        final Object other$user = other.getUser();
        if (this$user == null ? other$user != null : !this$user.equals(other$user)) {
            return false;
        }
        final Object this$datetime = this.getDatetime();
        final Object other$datetime = other.getDatetime();
        if (this$datetime == null ? other$datetime != null : !this$datetime.equals(other$datetime)) {
            return false;
        }
        final Object this$objectType = this.getObjectType();
        final Object other$objectType = other.getObjectType();
        if (this$objectType == null ? other$objectType != null : !this$objectType.equals(other$objectType)) {
            return false;
        }
        final Object this$objectId = this.getObjectId();
        final Object other$objectId = other.getObjectId();
        if (this$objectId == null ? other$objectId != null : !this$objectId.equals(other$objectId)) {
            return false;
        }
        final Object this$subject = this.getSubject();
        final Object other$subject = other.getSubject();
        if (this$subject == null ? other$subject != null : !this$subject.equals(other$subject)) {
            return false;
        }
        final Object this$subjectRich = this.getSubjectRich();
        final Object other$subjectRich = other.getSubjectRich();
        if (this$subjectRich == null ? other$subjectRich != null : !this$subjectRich.equals(other$subjectRich)) {
            return false;
        }
        final Object this$subjectRichParameters = this.getSubjectRichParameters();
        final Object other$subjectRichParameters = other.getSubjectRichParameters();
        if (this$subjectRichParameters == null ? other$subjectRichParameters != null : !this$subjectRichParameters.equals(other$subjectRichParameters)) {
            return false;
        }
        final Object this$message = this.getMessage();
        final Object other$message = other.getMessage();
        if (this$message == null ? other$message != null : !this$message.equals(other$message)) {
            return false;
        }
        final Object this$messageRich = this.getMessageRich();
        final Object other$messageRich = other.getMessageRich();
        if (this$messageRich == null ? other$messageRich != null : !this$messageRich.equals(other$messageRich)) {
            return false;
        }
        final Object this$messageRichParameters = this.getMessageRichParameters();
        final Object other$messageRichParameters = other.getMessageRichParameters();
        if (this$messageRichParameters == null ? other$messageRichParameters != null : !this$messageRichParameters.equals(other$messageRichParameters)) {
            return false;
        }
        final Object this$link = this.getLink();
        final Object other$link = other.getLink();
        if (this$link == null ? other$link != null : !this$link.equals(other$link)) {
            return false;
        }
        final Object this$actions = this.getActions();
        final Object other$actions = other.getActions();

        return this$actions == null ? other$actions == null : this$actions.equals(other$actions);
    }

    protected boolean canEqual(final Object other) {
        return other instanceof Notification;
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        final Object $icon = this.getIcon();
        result = result * PRIME + ($icon == null ? 43 : $icon.hashCode());
        result = result * PRIME + this.getNotificationId();
        final Object $app = this.getApp();
        result = result * PRIME + ($app == null ? 43 : $app.hashCode());
        final Object $user = this.getUser();
        result = result * PRIME + ($user == null ? 43 : $user.hashCode());
        final Object $datetime = this.getDatetime();
        result = result * PRIME + ($datetime == null ? 43 : $datetime.hashCode());
        final Object $objectType = this.getObjectType();
        result = result * PRIME + ($objectType == null ? 43 : $objectType.hashCode());
        final Object $objectId = this.getObjectId();
        result = result * PRIME + ($objectId == null ? 43 : $objectId.hashCode());
        final Object $subject = this.getSubject();
        result = result * PRIME + ($subject == null ? 43 : $subject.hashCode());
        final Object $subjectRich = this.getSubjectRich();
        result = result * PRIME + ($subjectRich == null ? 43 : $subjectRich.hashCode());
        final Object $subjectRichParameters = this.getSubjectRichParameters();
        result = result * PRIME + ($subjectRichParameters == null ? 43 : $subjectRichParameters.hashCode());
        final Object $message = this.getMessage();
        result = result * PRIME + ($message == null ? 43 : $message.hashCode());
        final Object $messageRich = this.getMessageRich();
        result = result * PRIME + ($messageRich == null ? 43 : $messageRich.hashCode());
        final Object $messageRichParameters = this.getMessageRichParameters();
        result = result * PRIME + ($messageRichParameters == null ? 43 : $messageRichParameters.hashCode());
        final Object $link = this.getLink();
        result = result * PRIME + ($link == null ? 43 : $link.hashCode());
        final Object $actions = this.getActions();
        result = result * PRIME + ($actions == null ? 43 : $actions.hashCode());
        return result;
    }

    public String toString() {
        return "Notification(icon=" + this.getIcon() + ", notificationId=" + this.getNotificationId() + ", app=" + this.getApp() + ", user=" + this.getUser() + ", datetime=" + this.getDatetime() + ", objectType=" + this.getObjectType() + ", objectId=" + this.getObjectId() + ", subject=" + this.getSubject() + ", subjectRich=" + this.getSubjectRich() + ", subjectRichParameters=" + this.getSubjectRichParameters() + ", message=" + this.getMessage() + ", messageRich=" + this.getMessageRich() + ", messageRichParameters=" + this.getMessageRichParameters() + ", link=" + this.getLink() + ", actions=" + this.getActions() + ")";
    }
}
