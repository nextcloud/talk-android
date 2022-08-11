/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.databinding.RvItemConversationWithLastMessageBinding;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.status.Status;
import com.nextcloud.talk.ui.StatusDrawable;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flexibleadapter.items.ISectionable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

public class ConversationItem extends AbstractFlexibleItem<ConversationItem.ConversationItemViewHolder> implements
    ISectionable<ConversationItem.ConversationItemViewHolder, GenericTextHeaderItem>, IFilterable<String> {

    public static final int VIEW_TYPE = R.layout.rv_item_conversation_with_last_message;

    private static final float STATUS_SIZE_IN_DP = 9f;

    private final Conversation conversation;
    private final User user;
    private final Context context;
    private GenericTextHeaderItem header;
    private final Status status;
    private final ViewThemeUtils viewThemeUtils;


    public ConversationItem(Conversation conversation, User user, Context activityContext, Status status, final ViewThemeUtils viewThemeUtils) {
        this.conversation = conversation;
        this.user = user;
        this.context = activityContext;
        this.status = status;
        this.viewThemeUtils = viewThemeUtils;
    }

    public ConversationItem(Conversation conversation, User user,
                            Context activityContext, GenericTextHeaderItem genericTextHeaderItem, Status status,
                            final ViewThemeUtils viewThemeUtils) {
        this(conversation, user, activityContext, status, viewThemeUtils);
        this.header = genericTextHeaderItem;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ConversationItem) {
            ConversationItem inItem = (ConversationItem) o;
            return conversation.equals(inItem.getModel()) && Objects.equals(status, inItem.status);
        }
        return false;
    }

    public Conversation getModel() {
        return conversation;
    }

    @Override
    public int hashCode() {
        int result = conversation.hashCode();
        result = 31 * result + (status != null ? status.hashCode() : 0);
        return result;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_conversation_with_last_message;
    }

    @Override
    public int getItemViewType() {
        return VIEW_TYPE;
    }

    @Override
    public ConversationItemViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new ConversationItemViewHolder(view, adapter);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter,
                               ConversationItemViewHolder holder,
                               int position,
                               List<Object> payloads) {
        Context appContext =
            NextcloudTalkApplication.Companion.getSharedApplication().getApplicationContext();
        holder.binding.dialogAvatar.setController(null);

        holder.binding.dialogName.setTextColor(ResourcesCompat.getColor(context.getResources(),
                                                                        R.color.conversation_item_header,
                                                                        null));

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.binding.dialogName, conversation.getDisplayName(),
                                        String.valueOf(adapter.getFilter(String.class)),
                                        viewThemeUtils.getElementColor(holder.binding.dialogName.getContext()));
        } else {
            holder.binding.dialogName.setText(conversation.getDisplayName());
        }

        if (conversation.getUnreadMessages() > 0) {
            holder.binding.dialogName.setTypeface(holder.binding.dialogName.getTypeface(), Typeface.BOLD);
            holder.binding.dialogLastMessage.setTypeface(holder.binding.dialogLastMessage.getTypeface(), Typeface.BOLD);
            holder.binding.dialogUnreadBubble.setVisibility(View.VISIBLE);
            if (conversation.getUnreadMessages() < 1000) {
                holder.binding.dialogUnreadBubble.setText(Long.toString(conversation.getUnreadMessages()));
            } else {
                holder.binding.dialogUnreadBubble.setText(R.string.tooManyUnreadMessages);
            }

            ColorStateList lightBubbleFillColor = ColorStateList.valueOf(
                ContextCompat.getColor(context,
                                       R.color.conversation_unread_bubble));
            int lightBubbleTextColor = ContextCompat.getColor(
                context,
                R.color.conversation_unread_bubble_text);

            if (conversation.getType() == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) {
                viewThemeUtils.colorChipBackground(holder.binding.dialogUnreadBubble);
            } else if (conversation.getUnreadMention()) {
                if (CapabilitiesUtilNew.hasSpreedFeatureCapability(user, "direct-mention-flag")) {
                    if (conversation.getUnreadMentionDirect()) {
                        viewThemeUtils.colorChipBackground(holder.binding.dialogUnreadBubble);
                    } else {
                        viewThemeUtils.colorChipOutlined(holder.binding.dialogUnreadBubble, 6.0f);
                    }
                } else {
                    viewThemeUtils.colorChipBackground(holder.binding.dialogUnreadBubble);
                }
            } else {
                holder.binding.dialogUnreadBubble.setChipBackgroundColor(lightBubbleFillColor);
                holder.binding.dialogUnreadBubble.setTextColor(lightBubbleTextColor);
            }
        } else {
            holder.binding.dialogName.setTypeface(null, Typeface.NORMAL);
            holder.binding.dialogDate.setTypeface(null, Typeface.NORMAL);
            holder.binding.dialogLastMessage.setTypeface(null, Typeface.NORMAL);
            holder.binding.dialogUnreadBubble.setVisibility(View.GONE);
        }

        if (conversation.getFavorite()) {
            holder.binding.favoriteConversationImageView.setVisibility(View.VISIBLE);
        } else {
            holder.binding.favoriteConversationImageView.setVisibility(View.GONE);
        }

        if (status != null && Conversation.ConversationType.ROOM_SYSTEM != conversation.getType()) {
            float size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, appContext);

            holder.binding.userStatusImage.setVisibility(View.VISIBLE);
            holder.binding.userStatusImage.setImageDrawable(new StatusDrawable(
                status.getStatus(),
                status.getIcon(),
                size,
                context.getResources().getColor(R.color.bg_default),
                appContext));
        } else {
            holder.binding.userStatusImage.setVisibility(View.GONE);
        }

        if (conversation.getLastMessage() != null) {
            holder.binding.dialogDate.setVisibility(View.VISIBLE);
            holder.binding.dialogDate.setText(
                DateUtils.getRelativeTimeSpanString(conversation.getLastActivity() * 1000L,
                                                    System.currentTimeMillis(),
                                                    0,
                                                    DateUtils.FORMAT_ABBREV_RELATIVE));

            if (!TextUtils.isEmpty(conversation.getLastMessage().getSystemMessage()) ||
                Conversation.ConversationType.ROOM_SYSTEM == conversation.getType()) {
                holder.binding.dialogLastMessage.setText(conversation.getLastMessage().getText());
            } else {
                String authorDisplayName = "";
                conversation.getLastMessage().setActiveUser(user);
                String text;
                if (conversation.getLastMessage().getCalculateMessageType() == ChatMessage.MessageType.REGULAR_TEXT_MESSAGE) {
                    if (conversation.getLastMessage().getActorId().equals(user.getUserId())) {
                        text = String.format(appContext.getString(R.string.nc_formatted_message_you),
                                             conversation.getLastMessage().getLastMessageDisplayText());
                    } else {
                        authorDisplayName = !TextUtils.isEmpty(conversation.getLastMessage().getActorDisplayName()) ?
                            conversation.getLastMessage().getActorDisplayName() :
                            "guests".equals(conversation.getLastMessage().getActorType()) ?
                                appContext.getString(R.string.nc_guest) : "";
                        text = String.format(appContext.getString(R.string.nc_formatted_message),
                                             authorDisplayName,
                                             conversation.getLastMessage().getLastMessageDisplayText());
                    }
                } else {
                    text = conversation.getLastMessage().getLastMessageDisplayText();
                }

                holder.binding.dialogLastMessage.setText(text);
            }
        } else {
            holder.binding.dialogDate.setVisibility(View.GONE);
            holder.binding.dialogLastMessage.setText(R.string.nc_no_messages_yet);
        }

        holder.binding.dialogAvatar.setVisibility(View.VISIBLE);

        boolean shouldLoadAvatar = true;
        String objectType;
        if (!TextUtils.isEmpty(objectType = conversation.getObjectType())) {
            switch (objectType) {
                case "share:password":
                    shouldLoadAvatar = false;
                    holder.binding.dialogAvatar.setImageDrawable(
                        ContextCompat.getDrawable(context,
                                                  R.drawable.ic_circular_lock));
                    break;
                case "file":
                    shouldLoadAvatar = false;
                    holder.binding.dialogAvatar.setImageDrawable(
                        ContextCompat.getDrawable(context,
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

                holder.binding.dialogAvatar.getHierarchy().setPlaceholderImage(
                    DisplayUtils.getRoundedDrawable(layerDrawable));
            } else {
                holder.binding.dialogAvatar.getHierarchy().setPlaceholderImage(R.mipmap.ic_launcher);
            }
            shouldLoadAvatar = false;
        }

        if (shouldLoadAvatar) {
            switch (conversation.getType()) {
                case ROOM_TYPE_ONE_TO_ONE_CALL:
                    if (!TextUtils.isEmpty(conversation.getName())) {
                        DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                            .setOldController(holder.binding.dialogAvatar.getController())
                            .setAutoPlayAnimations(true)
                            .setImageRequest(DisplayUtils.getImageRequestForUrl(
                                ApiUtils.getUrlForAvatar(user.getBaseUrl(),
                                                         conversation.getName(),
                                                         false),
                                user))
                            .build();
                        holder.binding.dialogAvatar.setController(draweeController);
                    } else {
                        holder.binding.dialogAvatar.setVisibility(View.GONE);
                    }
                    break;
                case ROOM_GROUP_CALL:
                    holder.binding.dialogAvatar.setImageDrawable(
                        ContextCompat.getDrawable(context,
                                                  R.drawable.ic_circular_group));
                    break;
                case ROOM_PUBLIC_CALL:
                    holder.binding.dialogAvatar.setImageDrawable(
                        ContextCompat.getDrawable(context,
                                                  R.drawable.ic_circular_link));
                    break;
                default:
                    holder.binding.dialogAvatar.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public boolean filter(String constraint) {
        return conversation.getDisplayName() != null &&
            Pattern
                .compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                .matcher(conversation.getDisplayName().trim())
                .find();
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

        RvItemConversationWithLastMessageBinding binding;

        ConversationItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            binding = RvItemConversationWithLastMessageBinding.bind(view);
        }
    }
}
