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

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.models.json.status.StatusType;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;

import java.util.List;
import java.util.regex.Pattern;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;

public class MentionAutocompleteItem extends AbstractFlexibleItem<UserItem.UserItemViewHolder>
        implements IFilterable<String> {

    public static final String SOURCE_CALLS = "calls";
    public static final String SOURCE_GUESTS = "guests";
    private String objectId;
    private String displayName;
    private String source;
    private String status;
    private String statusIcon;
    private UserEntity currentUser;
    private Context context;

    public MentionAutocompleteItem(
            Mention mention,
            UserEntity currentUser,
            Context activityContext) {
        this.objectId = mention.getId();
        this.displayName = mention.getLabel();
        this.source = mention.getSource();
        this.status = mention.getStatus();
        this.statusIcon = mention.getStatusIcon();
        this.currentUser = currentUser;
        this.context = activityContext;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getObjectId() {
        return objectId;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MentionAutocompleteItem) {
            MentionAutocompleteItem inItem = (MentionAutocompleteItem) o;
            return (objectId.equals(inItem.objectId) && displayName.equals(inItem.displayName));
        }

        return false;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_mention;
    }

    @Override
    public UserItem.UserItemViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new UserItem.UserItemViewHolder(view, adapter);
    }


    @SuppressLint("SetTextI18n")
    @Override
    public void bindViewHolder(
            FlexibleAdapter<IFlexible> adapter,
            UserItem.UserItemViewHolder holder,
            int position,
            List<Object> payloads) {

        holder.contactDisplayName.setTextColor(ResourcesCompat.getColor(context.getResources(),
                                                                        R.color.conversation_item_header,
                                                                        null));
        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.contactDisplayName,
                                        displayName,
                                        String.valueOf(adapter.getFilter(String.class)),
                                        NextcloudTalkApplication.Companion.getSharedApplication()
                                                .getResources().getColor(R.color.colorPrimary));
            if (holder.contactMentionId != null) {
                FlexibleUtils.highlightText(holder.contactMentionId,
                                            "@" + objectId,
                                            String.valueOf(adapter.getFilter(String.class)),
                                            NextcloudTalkApplication.Companion.getSharedApplication()
                                                    .getResources().getColor(R.color.colorPrimary));
            }
        } else {
            holder.contactDisplayName.setText(displayName);
            if (holder.contactMentionId != null) {
                holder.contactMentionId.setText("@" + objectId);
            }
        }

        if (SOURCE_CALLS.equals(source)) {
            holder.participantAvatar.setImageResource(R.drawable.ic_circular_group);
        } else {
            String avatarId = objectId;
            String avatarUrl = ApiUtils.getUrlForAvatarWithName(currentUser.getBaseUrl(),
                                                                avatarId, R.dimen.avatar_size_big);

            if (SOURCE_GUESTS.equals(source)) {
                avatarId = displayName;
                avatarUrl = ApiUtils.getUrlForAvatarWithNameForGuests(
                        currentUser.getBaseUrl(),
                        avatarId,
                        R.dimen.avatar_size_big);
            }

            holder.participantAvatar.setController(null);
            DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                    .setOldController(holder.participantAvatar.getController())
                    .setAutoPlayAnimations(true)
                    .setImageRequest(DisplayUtils.getImageRequestForUrl(avatarUrl, null))
                    .build();
            holder.participantAvatar.setController(draweeController);
        }
        if (status != null && status.equals(StatusType.DND.getString())) {
            setOnlineStateIcon(holder, R.drawable.ic_user_status_dnd_with_border);
        } else if (statusIcon != null && statusIcon.isEmpty()) {
            holder.participantOnlineStateImage.setVisibility(View.GONE);
            holder.participantEmoji.setVisibility(View.VISIBLE);
            holder.participantEmoji.setText(statusIcon);
        } else if (status != null && status.equals(StatusType.AWAY.getString())) {
            setOnlineStateIcon(holder, R.drawable.ic_user_status_away_with_border);
        } else if (status != null && status.equals(StatusType.ONLINE.getString())) {
            setOnlineStateIcon(holder, R.drawable.online_status_with_border);
        } else {
            holder.participantEmoji.setVisibility(View.GONE);
            holder.participantOnlineStateImage.setVisibility(View.GONE);
        }
    }

    private void setOnlineStateIcon(UserItem.UserItemViewHolder holder, int icon) {
        holder.participantEmoji.setVisibility(View.GONE);
        holder.participantOnlineStateImage.setVisibility(View.VISIBLE);
        holder.participantOnlineStateImage.setImageDrawable(ContextCompat.getDrawable(context, icon));
    }

    @Override
    public boolean filter(String constraint) {
        return objectId != null &&
                Pattern
                        .compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                        .matcher(objectId)
                        .find() ||
                displayName != null &&
                        Pattern
                                .compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                                .matcher(displayName)
                                .find();
    }
}
