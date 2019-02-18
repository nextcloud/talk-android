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

package com.nextcloud.talk.models.json.notifications;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;
import lombok.Data;
import org.parceler.Parcel;

import java.util.Date;
import java.util.HashMap;
import java.util.List;

@Data
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
    @JsonField(name = "datetime")
    Date datetime;
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
}
