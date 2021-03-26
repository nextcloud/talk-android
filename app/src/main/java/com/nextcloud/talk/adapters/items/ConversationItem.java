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

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.emoji.widget.EmojiTextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.google.android.material.chip.Chip;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

import java.util.List;
import java.util.regex.Pattern;

public class ConversationItem extends AbstractFlexibleItem<ConversationItem.ConversationItemViewHolder> implements
        IFilterable<String> {


    private Conversation conversation;
    private UserEntity userEntity;
    private Context context;

    public ConversationItem(Conversation conversation, UserEntity userEntity,
                            Context activityContext) {
        this.conversation = conversation;
        this.userEntity = userEntity;
        this.context = activityContext;
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

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, ConversationItemViewHolder holder, int position, List<Object> payloads) {
        Context appContext =
                NextcloudTalkApplication.Companion.getSharedApplication().getApplicationContext();
        holder.dialogAvatar.setController(null);

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.dialogName, conversation.getDisplayName(),
                    String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.Companion.getSharedApplication()
                            .getResources().getColor(R.color.colorPrimary));
        } else {
            holder.dialogName.setText(conversation.getDisplayName());
        }

        if (conversation.getUnreadMessages() > 0) {
            holder.dialogName.setTypeface(
                    holder.dialogName.getTypeface(),
                    Typeface.BOLD
            );
            holder.dialogDate.setTypeface(
                    holder.dialogDate.getTypeface(),
                    Typeface.BOLD
            );
            holder.dialogLastMessage.setTypeface(
                    holder.dialogLastMessage.getTypeface(),
                    Typeface.BOLD
            );
            holder.dialogUnreadBubble.setVisibility(View.VISIBLE);
            if (conversation.getUnreadMessages() < 1000) {
                holder.dialogUnreadBubble.setText(Long.toString(conversation.getUnreadMessages()));
            } else {
                holder.dialogUnreadBubble.setText(R.string.tooManyUnreadMessages);
            }

            if (conversation.isUnreadMention() || conversation.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
                holder.dialogUnreadBubble.setChipBackgroundColorResource(R.color.colorPrimary);
                holder.dialogUnreadBubble.setTextColor(Color.WHITE);
            } else {
                holder.dialogUnreadBubble.setChipBackgroundColorResource(R.color.conversation_unread_bubble);
                holder.dialogUnreadBubble.setTextColor(
                        ContextCompat.getColor(context, R.color.conversation_unread_bubble_text));
            }
        } else {
            holder.dialogUnreadBubble.setVisibility(View.GONE);
        }


        if (conversation.isHasPassword()) {
            holder.passwordProtectedRoomImageView.setVisibility(View.VISIBLE);
        } else {
            holder.passwordProtectedRoomImageView.setVisibility(View.GONE);
        }

        if (conversation.isFavorite()) {
            holder.pinnedConversationImageView.setVisibility(View.VISIBLE);
        } else {
            holder.pinnedConversationImageView.setVisibility(View.GONE);
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
                    holder.dialogAvatar.getHierarchy().setImage(new BitmapDrawable(DisplayUtils
                            .getRoundedBitmapFromVectorDrawableResource(context.getResources(),
                                    R.drawable.ic_file_password_request)), 100, true);
                    break;
                case "file":
                    shouldLoadAvatar = false;
                    holder.dialogAvatar.getHierarchy().setImage(new BitmapDrawable(DisplayUtils
                            .getRoundedBitmapFromVectorDrawableResource(context.getResources(),
                                    R.drawable.ic_file_icon)), 100, true);
                    break;
                default:
                    break;
            }
        }

        if (Conversation.ConversationType.ROOM_SYSTEM.equals(conversation.getType())) {
            Drawable[] layers = new Drawable[2];
            layers[0] = context.getDrawable(R.drawable.ic_launcher_background);
            layers[1] = context.getDrawable(R.drawable.ic_launcher_foreground);
            LayerDrawable layerDrawable = new LayerDrawable(layers);

            holder.dialogAvatar.getHierarchy().setPlaceholderImage(DisplayUtils.getRoundedDrawable(layerDrawable));

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
                        holder.dialogAvatar.getHierarchy().setImage(new BitmapDrawable(DisplayUtils.getRoundedBitmapFromVectorDrawableResource(context.getResources(), R.drawable.ic_people_group_white_24px)), 100, true);
                    break;
                case ROOM_PUBLIC_CALL:
                        holder.dialogAvatar.getHierarchy().setImage(new BitmapDrawable(DisplayUtils
                                .getRoundedBitmapFromVectorDrawableResource(context.getResources(),
                                        R.drawable.ic_link_white_24px)), 100, true);
                    break;
                default:
                    holder.dialogAvatar.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public boolean filter(String constraint) {
        return conversation.getDisplayName() != null &&
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL).matcher(conversation.getDisplayName().trim()).find();
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
        @BindView(R.id.passwordProtectedRoomImageView)
        ImageView passwordProtectedRoomImageView;
        @BindView(R.id.favoriteConversationImageView)
        ImageView pinnedConversationImageView;

        ConversationItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }
}
