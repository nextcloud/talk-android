/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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

package com.nextcloud.talk.adapters.items;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.chip.Chip;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.CapabilitiesUtil;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.status.Status;
import com.nextcloud.talk.models.json.status.StatusType;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;

import java.util.List;
import java.util.regex.Pattern;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.emoji.widget.EmojiTextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flexibleadapter.items.ISectionable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

public class ConversationItem extends AbstractFlexibleItem<ConversationItem.ConversationItemViewHolder> implements ISectionable<ConversationItem.ConversationItemViewHolder, GenericTextHeaderItem>,
    IFilterable<String> {


    private Conversation conversation;
    private UserEntity userEntity;
    private Context context;
    private GenericTextHeaderItem header;
    private Status status;

    public ConversationItem(Conversation conversation, UserEntity userEntity, Context activityContext, Status status) {
        this.conversation = conversation;
        this.userEntity = userEntity;
        this.context = activityContext;
        this.status = status;
    }

    public ConversationItem(Conversation conversation, UserEntity userEntity,
                            Context activityContext, GenericTextHeaderItem genericTextHeaderItem, Status status) {
        this.conversation = conversation;
        this.userEntity = userEntity;
        this.context = activityContext;
        this.header = genericTextHeaderItem;
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ConversationItem) {
            ConversationItem inItem = (ConversationItem) o;
            return conversation.equals(inItem.getModel());
        }
        return false;
    }

    public Conversation getModel() {
        return conversation;
    }

    @Override
    public int hashCode() {
        return conversation.hashCode();
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_conversation_with_last_message;
    }

    @Override
    public ConversationItemViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new ConversationItemViewHolder(view, adapter);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, ConversationItemViewHolder holder, int position, List<Object> payloads) {
        Context appContext =
            NextcloudTalkApplication.Companion.getSharedApplication().getApplicationContext();
        holder.dialogAvatar.setController(null);

        holder.dialogName.setTextColor(ResourcesCompat.getColor(context.getResources(),
                                                                R.color.conversation_item_header,
                                                                null));

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.dialogName, conversation.getDisplayName(),
                                        String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.Companion.getSharedApplication()
                                            .getResources().getColor(R.color.colorPrimary));
        } else {
            holder.dialogName.setText(conversation.getDisplayName());
        }

        if (conversation.getUnreadMessages() > 0) {
            holder.dialogName.setTypeface(holder.dialogName.getTypeface(), Typeface.BOLD);
            holder.dialogLastMessage.setTypeface(holder.dialogLastMessage.getTypeface(), Typeface.BOLD);
            holder.dialogUnreadBubble.setVisibility(View.VISIBLE);
            if (conversation.getUnreadMessages() < 1000) {
                holder.dialogUnreadBubble.setText(Long.toString(conversation.getUnreadMessages()));
            } else {
                holder.dialogUnreadBubble.setText(R.string.tooManyUnreadMessages);
            }

            ColorStateList lightBubbleFillColor = ColorStateList.valueOf(
                ContextCompat.getColor(context,
                                       R.color.conversation_unread_bubble));
            int lightBubbleTextColor = ContextCompat.getColor(
                context,
                R.color.conversation_unread_bubble_text);
            ColorStateList lightBubbleStrokeColor = ColorStateList.valueOf(
                ContextCompat.getColor(context,
                                       R.color.colorPrimary));

            if (conversation.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
                holder.dialogUnreadBubble.setChipBackgroundColorResource(R.color.colorPrimary);
                holder.dialogUnreadBubble.setTextColor(Color.WHITE);
            } else if (conversation.isUnreadMention()) {
                if (CapabilitiesUtil.hasSpreedFeatureCapability(userEntity, "direct-mention-flag")) {
                    if (conversation.getUnreadMentionDirect()) {
                        holder.dialogUnreadBubble.setChipBackgroundColorResource(R.color.colorPrimary);
                        holder.dialogUnreadBubble.setTextColor(Color.WHITE);
                    } else {
                        holder.dialogUnreadBubble.setChipBackgroundColorResource(R.color.bg_default);
                        holder.dialogUnreadBubble.setTextColor(ContextCompat.getColor(
                            context,
                            R.color.colorPrimary));
                        holder.dialogUnreadBubble.setChipStrokeWidth(6.0f);
                        holder.dialogUnreadBubble.setChipStrokeColor(lightBubbleStrokeColor);
                    }
                } else {
                    holder.dialogUnreadBubble.setChipBackgroundColorResource(R.color.colorPrimary);
                    holder.dialogUnreadBubble.setTextColor(Color.WHITE);
                }
            } else {
                holder.dialogUnreadBubble.setChipBackgroundColor(lightBubbleFillColor);
                holder.dialogUnreadBubble.setTextColor(lightBubbleTextColor);
            }
        } else {
            holder.dialogName.setTypeface(null, Typeface.NORMAL);
            holder.dialogDate.setTypeface(null, Typeface.NORMAL);
            holder.dialogLastMessage.setTypeface(null, Typeface.NORMAL);
            holder.dialogUnreadBubble.setVisibility(View.GONE);
        }

        if (conversation.isFavorite()) {
            holder.pinnedConversationImageView.setVisibility(View.VISIBLE);
        } else {
            holder.pinnedConversationImageView.setVisibility(View.GONE);
        }

        if (status != null && status.getStatus().equals(StatusType.DND.getString())) {
            setOnlineStateIcon(holder, R.drawable.ic_user_status_dnd_with_border);
        } else if (status != null && status.getIcon() != null && !status.getIcon().isEmpty()) {
            holder.userStatusOnlineState.setVisibility(View.GONE);
            holder.userStatusEmoji.setVisibility(View.VISIBLE);
            holder.userStatusEmoji.setText(status.getIcon());
        } else if (status != null && status.getStatus().equals(StatusType.AWAY.getString())) {
            setOnlineStateIcon(holder, R.drawable.ic_user_status_away_with_border);
        } else if (status != null && status.getStatus().equals(StatusType.ONLINE.getString())) {
            setOnlineStateIcon(holder, R.drawable.online_status_with_border);
        } else {
            holder.userStatusEmoji.setVisibility(View.GONE);
            holder.userStatusOnlineState.setVisibility(View.GONE);
        }

        if (conversation.getLastMessage() != null) {
            holder.dialogDate.setVisibility(View.VISIBLE);
            holder.dialogDate.setText(DateUtils.getRelativeTimeSpanString(conversation.getLastActivity() * 1000L,
                                                                          System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE));

            if (!TextUtils.isEmpty(conversation.getLastMessage().getSystemMessage()) || Conversation.ConversationType.ROOM_SYSTEM.equals(conversation.getType())) {
                holder.dialogLastMessage.setText(conversation.getLastMessage().getText());
            } else {
                String authorDisplayName = "";
                conversation.getLastMessage().setActiveUser(userEntity);
                String text;
                if (conversation.getLastMessage().getMessageType().equals(ChatMessage.MessageType.REGULAR_TEXT_MESSAGE)) {
                    if (conversation.getLastMessage().getActorId().equals(userEntity.getUserId())) {
                        text = String.format(appContext.getString(R.string.nc_formatted_message_you),
                                             conversation.getLastMessage().getLastMessageDisplayText());
                    } else {
                        authorDisplayName = !TextUtils.isEmpty(conversation.getLastMessage().getActorDisplayName()) ?
                            conversation.getLastMessage().getActorDisplayName() :
                            "guests".equals(conversation.getLastMessage().getActorType()) ? appContext.getString(R.string.nc_guest) : "";
                        text = String.format(appContext.getString(R.string.nc_formatted_message),
                                             authorDisplayName,
                                             conversation.getLastMessage().getLastMessageDisplayText());
                    }
                } else {
                    text = conversation.getLastMessage().getLastMessageDisplayText();
                }

                holder.dialogLastMessage.setText(text);
            }
        } else {
            holder.dialogDate.setVisibility(View.GONE);
            holder.dialogLastMessage.setText(R.string.nc_no_messages_yet);
        }

        holder.dialogAvatar.setVisibility(View.VISIBLE);

        boolean shouldLoadAvatar = true;
        String objectType;
        if (!TextUtils.isEmpty(objectType = conversation.getObjectType())) {
            switch (objectType) {
                case "share:password":
                    shouldLoadAvatar = false;
                    holder.dialogAvatar.setImageDrawable(ContextCompat.getDrawable(context,
                                                                                   R.drawable.ic_circular_lock));
                    break;
                case "file":
                    shouldLoadAvatar = false;
                    holder.dialogAvatar.setImageDrawable(ContextCompat.getDrawable(context,
                                                                                   R.drawable.ic_circular_document));
                    break;
                default:
                    break;
            }
        }

        if (Conversation.ConversationType.ROOM_SYSTEM.equals(conversation.getType())) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Drawable[] layers = new Drawable[2];
                layers[0] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_background);
                layers[1] = ContextCompat.getDrawable(context, R.drawable.ic_launcher_foreground);
                LayerDrawable layerDrawable = new LayerDrawable(layers);

                holder.dialogAvatar.getHierarchy().setPlaceholderImage(DisplayUtils.getRoundedDrawable(layerDrawable));
            } else {
                holder.dialogAvatar.getHierarchy().setPlaceholderImage(R.mipmap.ic_launcher);
            }
            shouldLoadAvatar = false;
        }

        if (shouldLoadAvatar) {
            switch (conversation.getType()) {
                case ROOM_TYPE_ONE_TO_ONE_CALL:
                    if (!TextUtils.isEmpty(conversation.getName())) {
                        DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                            .setOldController(holder.dialogAvatar.getController())
                            .setAutoPlayAnimations(true)
                            .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(), conversation.getName(), R.dimen.avatar_size), userEntity))
                            .build();
                        holder.dialogAvatar.setController(draweeController);
                    } else {
                        holder.dialogAvatar.setVisibility(View.GONE);
                    }
                    break;
                case ROOM_GROUP_CALL:
                    holder.dialogAvatar.setImageDrawable(ContextCompat.getDrawable(context,
                                                                                   R.drawable.ic_circular_group));
                    break;
                case ROOM_PUBLIC_CALL:
                    holder.dialogAvatar.setImageDrawable(ContextCompat.getDrawable(context,
                                                                                   R.drawable.ic_circular_link));
                    break;
                default:
                    holder.dialogAvatar.setVisibility(View.GONE);
            }
        }
    }

    private void setOnlineStateIcon(ConversationItemViewHolder holder, int icon) {
        holder.userStatusEmoji.setVisibility(View.GONE);
        holder.userStatusOnlineState.setVisibility(View.VISIBLE);
        holder.userStatusOnlineState.setImageDrawable(ContextCompat.getDrawable(context, icon));
    }

    @Override
    public boolean filter(String constraint) {
        return conversation.getDisplayName() != null &&
            Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL).matcher(conversation.getDisplayName().trim()).find();
    }

    @Override
    public GenericTextHeaderItem getHeader() {
        return header;
    }

    @Override
    public void setHeader(GenericTextHeaderItem header) {
        this.header = header;
    }

    static class ConversationItemViewHolder extends FlexibleViewHolder {
        @BindView(R.id.dialogAvatar)
        SimpleDraweeView dialogAvatar;
        @BindView(R.id.dialogName)
        EmojiTextView dialogName;
        @BindView(R.id.dialogDate)
        TextView dialogDate;
        @BindView(R.id.dialogLastMessage)
        EmojiTextView dialogLastMessage;
        @BindView(R.id.dialogUnreadBubble)
        Chip dialogUnreadBubble;
        @BindView(R.id.favoriteConversationImageView)
        ImageView pinnedConversationImageView;
        @BindView(R.id.userStatusEmoji)
        com.vanniktech.emoji.EmojiEditText userStatusEmoji;
        @BindView(R.id.userStatusOnlineState)
        ImageView userStatusOnlineState;


        ConversationItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }
}
