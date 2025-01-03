/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Christian Reiner <foss@christian-reiner.info>
 * SPDX-FileCopyrightText: 2020 Tobias Kaminsky <tobias.kaminsky@nextcloud.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.messages;

import com.nextcloud.talk.chat.ChatActivity;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.ViewHolder;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessageHolders;
import com.stfalcon.chatkit.messages.MessagesListAdapter;

import java.util.List;

public class TalkMessagesListAdapter<M extends IMessage> extends MessagesListAdapter<M> {
    private final ChatActivity chatActivity;

    public TalkMessagesListAdapter(
        String senderId,
        MessageHolders holders,
        ImageLoader imageLoader,
        ChatActivity chatActivity) {
        super(senderId, holders, imageLoader);
        this.chatActivity = chatActivity;
    }
    
    public List<MessagesListAdapter.Wrapper> getItems() {
        return items;
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {

        if (holder instanceof IncomingTextMessageViewHolder holderInstance) {
            holderInstance.assignCommonMessageInterface(chatActivity);
        } else if (holder instanceof OutcomingTextMessageViewHolder holderInstance) {
            holderInstance.assignCommonMessageInterface(chatActivity);
            holderInstance.adjustIfNoteToSelf(holderInstance, chatActivity.getCurrentConversation());

        } else if (holder instanceof IncomingLocationMessageViewHolder holderInstance) {
            holderInstance.assignCommonMessageInterface(chatActivity);
        } else if (holder instanceof OutcomingLocationMessageViewHolder holderInstance) {
            holderInstance.assignCommonMessageInterface(chatActivity);
            holderInstance.adjustIfNoteToSelf(holderInstance, chatActivity.getCurrentConversation());

        } else if (holder instanceof IncomingLinkPreviewMessageViewHolder holderInstance) {
            holderInstance.assignCommonMessageInterface(chatActivity);
        } else if (holder instanceof OutcomingLinkPreviewMessageViewHolder holderInstance) {
            holderInstance.assignCommonMessageInterface(chatActivity);
            holderInstance.adjustIfNoteToSelf(holderInstance, chatActivity.getCurrentConversation());

        } else if (holder instanceof IncomingVoiceMessageViewHolder holderInstance) {
            holderInstance.assignVoiceMessageInterface(chatActivity);
            holderInstance.assignCommonMessageInterface(chatActivity);
        } else if (holder instanceof OutcomingVoiceMessageViewHolder holderInstance) {
            holderInstance.assignVoiceMessageInterface(chatActivity);
            holderInstance.assignCommonMessageInterface(chatActivity);
            holderInstance.adjustIfNoteToSelf(holderInstance, chatActivity.getCurrentConversation());

        } else if (holder instanceof PreviewMessageViewHolder holderInstance) {
            holderInstance.assignPreviewMessageInterface(chatActivity);
            holderInstance.assignCommonMessageInterface(chatActivity);

        } else if (holder instanceof SystemMessageViewHolder holderInstance) {
            holderInstance.assignSystemMessageInterface(chatActivity);

        } else if (holder instanceof IncomingDeckCardViewHolder holderInstance) {
            holderInstance.assignCommonMessageInterface(chatActivity);
        } else if (holder instanceof OutcomingDeckCardViewHolder holderInstance) {
            holderInstance.assignCommonMessageInterface(chatActivity);
            holderInstance.adjustIfNoteToSelf(holderInstance, chatActivity.getCurrentConversation());
        }

        super.onBindViewHolder(holder, position);
    }
}
