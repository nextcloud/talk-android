/*
 * Nextcloud Talk - Android Client
 *
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


        if (holder instanceof IncomingTextMessageViewHolder) {
            ((IncomingTextMessageViewHolder) holder).assignCommonMessageInterface(chatActivity);
        } else if (holder instanceof OutcomingTextMessageViewHolder) {
            ((OutcomingTextMessageViewHolder) holder).assignCommonMessageInterface(chatActivity);

        } else if (holder instanceof IncomingLocationMessageViewHolder) {
            ((IncomingLocationMessageViewHolder) holder).assignCommonMessageInterface(chatActivity);
        } else if (holder instanceof OutcomingLocationMessageViewHolder) {
            ((OutcomingLocationMessageViewHolder) holder).assignCommonMessageInterface(chatActivity);

        } else if (holder instanceof IncomingLinkPreviewMessageViewHolder) {
            ((IncomingLinkPreviewMessageViewHolder) holder).assignCommonMessageInterface(chatActivity);
        } else if (holder instanceof OutcomingLinkPreviewMessageViewHolder) {
            ((OutcomingLinkPreviewMessageViewHolder) holder).assignCommonMessageInterface(chatActivity);

        } else if (holder instanceof IncomingVoiceMessageViewHolder) {
            ((IncomingVoiceMessageViewHolder) holder).assignVoiceMessageInterface(chatActivity);
            ((IncomingVoiceMessageViewHolder) holder).assignCommonMessageInterface(chatActivity);
        } else if (holder instanceof OutcomingVoiceMessageViewHolder) {
            ((OutcomingVoiceMessageViewHolder) holder).assignVoiceMessageInterface(chatActivity);
            ((OutcomingVoiceMessageViewHolder) holder).assignCommonMessageInterface(chatActivity);

        } else if (holder instanceof PreviewMessageViewHolder) {
            ((PreviewMessageViewHolder) holder).assignPreviewMessageInterface(chatActivity);
            ((PreviewMessageViewHolder) holder).assignCommonMessageInterface(chatActivity);

        } else if (holder instanceof SystemMessageViewHolder) {
            ((SystemMessageViewHolder) holder).assignSystemMessageInterface(chatActivity);
        } else if (holder instanceof CallStartedViewHolder) {
            ((CallStartedViewHolder) holder).assignCallStartedMessageInterface(chatActivity);
        } else if (holder instanceof TemporaryMessageViewHolder) {
            ((TemporaryMessageViewHolder) holder).assignTemporaryMessageInterface(chatActivity);
        }else if (holder instanceof IncomingDeckCardViewHolder){
            ((IncomingDeckCardViewHolder) holder).assignCommonMessageInterface(chatActivity);
        } else if(holder instanceof OutgoingDeckCardViewHolder){
            ((OutgoingDeckCardViewHolder) holder).assignCommonMessageInterface(chatActivity);
        }

        super.onBindViewHolder(holder, position);
    }
}
