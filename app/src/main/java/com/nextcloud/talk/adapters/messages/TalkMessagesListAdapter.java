/*
 * Nextcloud Talk application
 *  
 * @author Tobias Kaminsky
 * Copyright (C) 2020 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
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

package com.nextcloud.talk.adapters.messages;

import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessageHolders;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.util.List;

public class TalkMessagesListAdapter<M extends IMessage> extends MessagesListAdapter<M> {

    public TalkMessagesListAdapter(String senderId, MessageHolders holders, ImageLoader imageLoader) {
        super(senderId, holders, imageLoader);
    }
    
    public List<MessagesListAdapter.Wrapper> getItems() {
        return items;
    }
}
