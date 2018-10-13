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

package com.nextcloud.talk.adapters.items;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.glide.GlideApp;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

public class ConversationItem extends AbstractFlexibleItem<ConversationItem.ConversationItemViewHolder> implements
        IFilterable<String> {


    private Conversation conversation;
    private UserEntity userEntity;

    public ConversationItem(Conversation conversation, UserEntity userEntity) {
        this.conversation = conversation;
        this.userEntity = userEntity;
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
        Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.dialogName, conversation.getDisplayName(),
                    String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.getSharedApplication()
                            .getResources().getColor(R.color.colorPrimary));
        } else {
            holder.dialogName.setText(conversation.getDisplayName());
        }

        if (conversation.getUnreadMessages() > 0) {
            holder.dialogUnreadBubble.setVisibility(View.VISIBLE);
            if (conversation.getUnreadMessages() < 10) {
                holder.dialogUnreadBubble.setText(Long.toString(conversation.getUnreadMessages()));
            } else {
                holder.dialogUnreadBubble.setText("9+");
            }

            if (conversation.isUnreadMention()) {
                holder.dialogUnreadBubble.setBackground(context.getDrawable(R.drawable.bubble_circle_unread_mention));
            } else {
                holder.dialogUnreadBubble.setBackground(context.getDrawable(R.drawable.bubble_circle_unread));
            }
        } else {
            holder.dialogUnreadBubble.setVisibility(View.GONE);
        }

        String authorDisplayName = "";

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

            if (conversation.getType() == Conversation.RoomType.ROOM_TYPE_ONE_TO_ONE_CALL || !(TextUtils.isEmpty(conversation.getLastMessage().getSystemMessage()))) {
                holder.dialogLastMessageUserAvatar.setVisibility(View.GONE);
                holder.dialogLastMessage.setText(conversation.getLastMessage().getText());
            } else {
                holder.dialogLastMessageUserAvatar.setVisibility(View.VISIBLE);
                if (conversation.getLastMessage().getActorId().equals(userEntity.getUserId())) {
                    authorDisplayName = context.getString(R.string.nc_chat_you) + ": ";
                } else {
                    if (!TextUtils.isEmpty(conversation.getLastMessage().getActorDisplayName())) {
                        authorDisplayName = conversation.getLastMessage().getActorDisplayName() + ": ";
                    } else {
                        authorDisplayName = context.getString(R.string.nc_nick_guest) + ": ";
                    }
                }

                String fullString = authorDisplayName + conversation.getLastMessage().getText();
                Spannable spannableString = new SpannableString(fullString);
                final StyleSpan boldStyleSpan = new StyleSpan(Typeface.BOLD);
                spannableString.setSpan(boldStyleSpan, 0, fullString.indexOf(":") + 1, Spannable
                        .SPAN_INCLUSIVE_INCLUSIVE);

                holder.dialogLastMessage.setText(spannableString);
                holder.dialogLastMessageUserAvatar.setVisibility(View.VISIBLE);

                int smallAvatarSize = Math.round(context.getResources().getDimension(R.dimen.small_item_height));

                if (conversation.getLastMessage().getActorType().equals("guests")) {
                    TextDrawable drawable = TextDrawable.builder().beginConfig().bold()
                            .endConfig().buildRound(String.valueOf(authorDisplayName.charAt(0)),
                                    context.getResources().getColor(R.color.nc_grey));
                    holder.dialogLastMessageUserAvatar.setImageDrawable(drawable);
                } else {
                    GlideUrl glideUrl = new GlideUrl(ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                            conversation.getLastMessage().getActorId(), R.dimen.small_item_height), new LazyHeaders.Builder()
                            .setHeader("Accept", "image/*")
                            .setHeader("User-Agent", ApiUtils.getUserAgent())
                            .build());

                    GlideApp.with(context)
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .load(glideUrl)
                            .centerInside()
                            .override(smallAvatarSize, smallAvatarSize)
                            .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                            .into(holder.dialogLastMessageUserAvatar);
                }
            }

        } else {
            holder.dialogDate.setVisibility(View.GONE);
            holder.dialogLastMessageUserAvatar.setVisibility(View.GONE);
            holder.dialogLastMessage.setText(R.string.nc_no_messages_yet);
        }

        int avatarSize = Math.round(context.getResources().getDimension(R.dimen.avatar_size));


        holder.dialogAvatar.setVisibility(View.VISIBLE);

        switch (conversation.getType()) {
            case ROOM_TYPE_ONE_TO_ONE_CALL:

                if (!TextUtils.isEmpty(conversation.getName())) {
                    GlideUrl glideUrl = new GlideUrl(ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                            conversation.getName(), R.dimen.avatar_size), new LazyHeaders.Builder()
                            .setHeader("Accept", "image/*")
                            .setHeader("User-Agent", ApiUtils.getUserAgent())
                            .build());

                    GlideApp.with(context)
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .load(glideUrl)
                            .centerInside()
                            .override(avatarSize, avatarSize)
                            .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                            .into(holder.dialogAvatar);

                } else {
                    holder.dialogAvatar.setVisibility(View.GONE);
                }
                break;
            case ROOM_GROUP_CALL:

                GlideApp.with(context)
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .load(R.drawable.ic_people_group_white_24px)
                        .centerInside()
                        .override(avatarSize, avatarSize)
                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                        .into(holder.dialogAvatar);
                break;
            case ROOM_PUBLIC_CALL:
                GlideApp.with(context)
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .load(R.drawable.ic_link_white_24px)
                        .centerInside()
                        .override(avatarSize, avatarSize)
                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                        .into(holder.dialogAvatar);

                break;
            default:
                holder.dialogAvatar.setVisibility(View.GONE);
        }


    }

    @Override
    public boolean filter(String constraint) {
        return conversation.getDisplayName() != null &&
                StringUtils.containsIgnoreCase(conversation.getDisplayName().trim(), constraint);
    }

    static class ConversationItemViewHolder extends FlexibleViewHolder {
        @BindView(R.id.dialogAvatar)
        ImageView dialogAvatar;
        @BindView(R.id.dialogName)
        TextView dialogName;
        @BindView(R.id.dialogDate)
        TextView dialogDate;
        @BindView(R.id.dialogLastMessageUserAvatar)
        ImageView dialogLastMessageUserAvatar;
        @BindView(R.id.dialogLastMessage)
        TextView dialogLastMessage;
        @BindView(R.id.dialogUnreadBubble)
        TextView dialogUnreadBubble;
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
