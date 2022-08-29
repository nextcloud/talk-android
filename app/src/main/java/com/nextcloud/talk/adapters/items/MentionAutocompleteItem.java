/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2021-2022 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
import android.os.Build;
import android.view.View;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.nextcloud.talk.R;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.models.json.status.StatusType;
import com.nextcloud.talk.ui.StatusDrawable;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;

import java.util.List;
import java.util.regex.Pattern;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;

public class MentionAutocompleteItem extends AbstractFlexibleItem<ParticipantItem.ParticipantItemViewHolder>
    implements IFilterable<String> {

    private static final float STATUS_SIZE_IN_DP = 9f;
    private static final String NO_ICON = "";
    public static final String SOURCE_CALLS = "calls";
    public static final String SOURCE_GUESTS = "guests";

    private String source;
    private final String objectId;
    private final String displayName;
    private final String status;
    private final String statusIcon;
    private final String statusMessage;
    private final User currentUser;
    private final Context context;
    private final ViewThemeUtils viewThemeUtils;

    public MentionAutocompleteItem(
        Mention mention,
        User currentUser,
        Context activityContext, ViewThemeUtils viewThemeUtils) {
        this.objectId = mention.getId();
        this.displayName = mention.getLabel();
        this.source = mention.getSource();
        this.status = mention.getStatus();
        this.statusIcon = mention.getStatusIcon();
        this.statusMessage = mention.getStatusMessage();
        this.currentUser = currentUser;
        this.context = activityContext;
        this.viewThemeUtils = viewThemeUtils;
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
        return R.layout.rv_item_conversation_info_participant;
    }

    @Override
    public ParticipantItem.ParticipantItemViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new ParticipantItem.ParticipantItemViewHolder(view, adapter);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter,
                               ParticipantItem.ParticipantItemViewHolder holder,
                               int position,
                               List<Object> payloads) {

        holder.binding.nameText.setTextColor(
            ResourcesCompat.getColor(context.getResources(),
                                     R.color.conversation_item_header,
                                     null));
        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.binding.nameText,
                                        displayName,
                                        String.valueOf(adapter.getFilter(String.class)),
                                        viewThemeUtils
                                            .getScheme(holder.binding.secondaryText.getContext())
                                            .getPrimary());
            FlexibleUtils.highlightText(holder.binding.secondaryText,
                                        "@" + objectId,
                                        String.valueOf(adapter.getFilter(String.class)),
                                        viewThemeUtils
                                            .getScheme(holder.binding.secondaryText.getContext())
                                            .getPrimary());
        } else {
            holder.binding.nameText.setText(displayName);
            holder.binding.secondaryText.setText("@" + objectId);
        }

        if (SOURCE_CALLS.equals(source)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                holder.binding.avatarDraweeView.getHierarchy().setPlaceholderImage(
                    DisplayUtils.getRoundedDrawable(
                        viewThemeUtils.talk.themePlaceholderAvatar(holder.binding.avatarDraweeView,
                                                              R.drawable.ic_avatar_group)));
            } else {
                holder.binding.avatarDraweeView.setImageResource(R.drawable.ic_circular_group);
            }
        } else {
            String avatarId = objectId;
            String avatarUrl = ApiUtils.getUrlForAvatar(currentUser.getBaseUrl(),
                                                        avatarId, true);

            if (SOURCE_GUESTS.equals(source)) {
                avatarId = displayName;
                avatarUrl = ApiUtils.getUrlForGuestAvatar(
                    currentUser.getBaseUrl(),
                    avatarId,
                    false);
            }

            holder.binding.avatarDraweeView.setController(null);

            DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(holder.binding.avatarDraweeView.getController())
                .setAutoPlayAnimations(true)
                .setImageRequest(DisplayUtils.getImageRequestForUrl(avatarUrl))
                .build();
            holder.binding.avatarDraweeView.setController(draweeController);
        }

        drawStatus(holder);
    }

    private void drawStatus(ParticipantItem.ParticipantItemViewHolder holder) {
        float size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, context);
        holder.binding.userStatusImage.setImageDrawable(new StatusDrawable(
            status,
            NO_ICON,
            size,
            context.getResources().getColor(R.color.bg_default),
            context));

        if (statusMessage != null) {
            holder.binding.conversationInfoStatusMessage.setText(statusMessage);
            alignUsernameVertical(holder, 0);
        } else {
            holder.binding.conversationInfoStatusMessage.setText("");
            alignUsernameVertical(holder, 10);
        }

        if (statusIcon != null && !statusIcon.isEmpty()) {
            holder.binding.participantStatusEmoji.setText(statusIcon);
        } else {
            holder.binding.participantStatusEmoji.setVisibility(View.GONE);
        }

        if (status != null && status.equals(StatusType.DND.getString())) {
            if (statusMessage == null || statusMessage.isEmpty()) {
                holder.binding.conversationInfoStatusMessage.setText(R.string.dnd);
            }
        } else if (status != null && status.equals(StatusType.AWAY.getString())) {
            if (statusMessage == null || statusMessage.isEmpty()) {
                holder.binding.conversationInfoStatusMessage.setText(R.string.away);
            }
        }
    }

    private void alignUsernameVertical(ParticipantItem.ParticipantItemViewHolder holder, float densityPixelsFromTop) {
        ConstraintLayout.LayoutParams layoutParams =
            (ConstraintLayout.LayoutParams) holder.binding.nameText.getLayoutParams();
        layoutParams.topMargin = (int) DisplayUtils.convertDpToPixel(densityPixelsFromTop, context);
        holder.binding.nameText.setLayoutParams(layoutParams);
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
