/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

package com.nextcloud.talk.models.json.converters;

import com.bluelinelabs.logansquare.typeconverters.IntBasedTypeConverter;
import com.nextcloud.talk.models.json.conversations.Conversation;

public class EnumRoomTypeConverter extends IntBasedTypeConverter<Conversation.ConversationType> {
  @Override
  public Conversation.ConversationType getFromInt(int i) {
    switch (i) {
      case 1:
        return Conversation.ConversationType.ONE_TO_ONE_CONVERSATION;
      case 2:
        return Conversation.ConversationType.GROUP_CONVERSATION;
      case 3:
        return Conversation.ConversationType.PUBLIC_CONVERSATION;
      case 4:
        return Conversation.ConversationType.SYSTEM_CONVERSATION;
      default:
        return Conversation.ConversationType.ONE_TO_ONE_CONVERSATION;
    }
  }

  @Override
  public int convertToInt(Conversation.ConversationType object) {
    switch (object) {
      case ONE_TO_ONE_CONVERSATION:
        return 1;
      case GROUP_CONVERSATION:
        return 2;
      case PUBLIC_CONVERSATION:
        return 3;
      case SYSTEM_CONVERSATION:
        return 4;
      default:
        return 1;
    }
  }
}
