/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
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
import android.content.res.Resources;
import android.text.TextUtils;
import android.view.View;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.databinding.RvItemConversationInfoParticipantBinding;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.Participant.InCallFlags;
import com.nextcloud.talk.models.json.status.StatusType;
import com.nextcloud.talk.ui.StatusDrawable;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;

import java.util.List;
import java.util.regex.Pattern;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.res.ResourcesCompat;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

public class ParticipantItem extends AbstractFlexibleItem<ParticipantItem.ParticipantItemViewHolder> implements
    IFilterable<String> {

    private static final float STATUS_SIZE_IN_DP = 9f;
    private static final String NO_ICON = "";

    private Context context;
    private Participant participant;
    private UserEntity userEntity;
    public boolean isOnline = true;

    public ParticipantItem(Context activityContext,
                           Participant participant,
                           UserEntity userEntity) {
        this.context = activityContext;
        this.participant = participant;
        this.userEntity = userEntity;
    }

    public Participant getModel() {
        return participant;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UserItem) {
            UserItem inItem = (UserItem) o;
            return participant.getActorType() == inItem.getModel().getActorType() &&
                participant.getActorId().equals(inItem.getModel().getActorId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return participant.hashCode();
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_conversation_info_participant;
    }

    @Override
    public ParticipantItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new ParticipantItemViewHolder(view, adapter);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bindViewHolder(FlexibleAdapter adapter, ParticipantItemViewHolder holder, int position, List payloads) {

        holder.binding.avatarDraweeView.setController(null);

        drawStatus(holder);

        if (!isOnline) {
            holder.binding.nameText.setTextColor(ResourcesCompat.getColor(
                holder.binding.nameText.getContext().getResources(),
                R.color.medium_emphasis_text,
                null)
                                                );
            holder.binding.avatarDraweeView.setAlpha(0.38f);
        } else {
            holder.binding.nameText.setTextColor(ResourcesCompat.getColor(
                holder.binding.nameText.getContext().getResources(),
                R.color.high_emphasis_text,
                null)
                                                );
            holder.binding.avatarDraweeView.setAlpha(1.0f);
        }

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.binding.nameText, participant.getDisplayName(),
                                        String.valueOf(adapter.getFilter(String.class)),
                                        NextcloudTalkApplication.Companion.getSharedApplication()
                                            .getResources()
                                            .getColor(R.color.colorPrimary));
        }

        holder.binding.nameText.setText(participant.getDisplayName());

        if (TextUtils.isEmpty(participant.getDisplayName()) &&
            (participant.getType().equals(Participant.ParticipantType.GUEST) ||
                participant.getType().equals(Participant.ParticipantType.USER_FOLLOWING_LINK))) {
            holder.binding.nameText.setText(NextcloudTalkApplication
                                                .Companion
                                                .getSharedApplication()
                                                .getString(R.string.nc_guest));
        }

        if (participant.getActorType() == Participant.ActorType.GROUPS ||
            "groups".equals(participant.getSource()) ||
            participant.getActorType() == Participant.ActorType.CIRCLES ||
            "circles".equals(participant.getSource())) {
            holder.binding.avatarDraweeView.setImageResource(R.drawable.ic_circular_group);
        } else if (participant.getActorType() == Participant.ActorType.EMAILS) {
            holder.binding.avatarDraweeView.setImageResource(R.drawable.ic_circular_mail);
        } else if (participant.getActorType() == Participant.ActorType.GUESTS ||
            Participant.ParticipantType.GUEST.equals(participant.getType()) ||
            Participant.ParticipantType.GUEST_MODERATOR.equals(participant.getType())) {

            String displayName = NextcloudTalkApplication.Companion.getSharedApplication()
                .getResources().getString(R.string.nc_guest);

            if (!TextUtils.isEmpty(participant.getDisplayName())) {
                displayName = participant.getDisplayName();
            }

            DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(holder.binding.avatarDraweeView.getController())
                .setAutoPlayAnimations(true)
                .setImageRequest(DisplayUtils.getImageRequestForUrl(
                    ApiUtils.getUrlForAvatarWithNameForGuests(userEntity.getBaseUrl(),
                                                              displayName, R.dimen.avatar_size), null))
                .build();
            holder.binding.avatarDraweeView.setController(draweeController);

        } else if (participant.getActorType() == Participant.ActorType.USERS ||
            participant.getSource().equals("users")) {
            DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(holder.binding.avatarDraweeView.getController())
                .setAutoPlayAnimations(true)
                .setImageRequest(DisplayUtils.getImageRequestForUrl(
                    ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                                                     participant.getActorId(), R.dimen.avatar_size), null))
                .build();
            holder.binding.avatarDraweeView.setController(draweeController);
        }

        Resources resources = NextcloudTalkApplication.Companion.getSharedApplication().getResources();

        Long inCallFlag = participant.getInCall();
        if ((inCallFlag & InCallFlags.WITH_PHONE) > 0) {
            holder.binding.videoCallIcon.setImageResource(R.drawable.ic_call_grey_600_24dp);
            holder.binding.videoCallIcon.setVisibility(View.VISIBLE);
            holder.binding.videoCallIcon.setContentDescription(
                resources.getString(R.string.nc_call_state_with_phone, participant.displayName));
        } else if ((inCallFlag & InCallFlags.WITH_VIDEO) > 0) {
            holder.binding.videoCallIcon.setImageResource(R.drawable.ic_videocam_grey_600_24dp);
            holder.binding.videoCallIcon.setVisibility(View.VISIBLE);
            holder.binding.videoCallIcon.setContentDescription(
                resources.getString(R.string.nc_call_state_with_video, participant.displayName));
        } else if (inCallFlag > InCallFlags.DISCONNECTED) {
            holder.binding.videoCallIcon.setImageResource(R.drawable.ic_mic_grey_600_24dp);
            holder.binding.videoCallIcon.setVisibility(View.VISIBLE);
            holder.binding.videoCallIcon.setContentDescription(
                resources.getString(R.string.nc_call_state_in_call, participant.displayName));
        } else {
            holder.binding.videoCallIcon.setVisibility(View.GONE);
        }

        if (holder.binding.secondaryText != null) {
            String userType = "";

            switch (new EnumParticipantTypeConverter().convertToInt(participant.getType())) {
                case 1:
                    //userType = NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_owner);
                    //break;
                case 2:
                case 6: // Guest moderator
                    userType = NextcloudTalkApplication
                        .Companion
                        .getSharedApplication()
                        .getString(R.string.nc_moderator);
                    break;
                case 3:
                    userType = NextcloudTalkApplication
                        .Companion
                        .getSharedApplication()
                        .getString(R.string.nc_user);
                    if (participant.getActorType() == Participant.ActorType.GROUPS) {
                        userType = NextcloudTalkApplication
                            .Companion
                            .getSharedApplication()
                            .getString(R.string.nc_group);
                    }
                    if (participant.getActorType() == Participant.ActorType.CIRCLES) {
                        userType = NextcloudTalkApplication
                            .Companion
                            .getSharedApplication()
                            .getString(R.string.nc_circle);
                    }
                    break;
                case 4:
                    userType = NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest);
                    if (participant.getActorType() == Participant.ActorType.EMAILS) {
                        userType = NextcloudTalkApplication
                            .Companion
                            .getSharedApplication()
                            .getString(R.string.nc_email);
                    }
                    break;
                case 5:
                    userType = NextcloudTalkApplication
                        .Companion
                        .getSharedApplication()
                        .getString(R.string.nc_following_link);
                    break;
                default:
                    break;
            }

            if (!userType.equals(NextcloudTalkApplication
                                     .Companion
                                     .getSharedApplication()
                                     .getString(R.string.nc_user))) {
                holder.binding.secondaryText.setText("(" + userType + ")");
            }
        }
    }

    private void drawStatus(ParticipantItemViewHolder holder) {
        if (holder.binding.conversationInfoStatusMessage != null &&
            holder.binding.participantStatusEmoji != null &&
            holder.binding.userStatusImage != null) {
            float size = DisplayUtils.convertDpToPixel(STATUS_SIZE_IN_DP, context);
            holder.binding.userStatusImage.setImageDrawable(new StatusDrawable(
                participant.status,
                NO_ICON,
                size,
                context.getResources().getColor(R.color.bg_default),
                context));

            if (participant.statusMessage != null) {
                holder.binding.conversationInfoStatusMessage.setText(participant.statusMessage);
                alignUsernameVertical(holder, 0);
            } else {
                holder.binding.conversationInfoStatusMessage.setText("");
                alignUsernameVertical(holder, 10);
            }

            if (participant.statusIcon != null && !participant.statusIcon.isEmpty()) {
                holder.binding.participantStatusEmoji.setText(participant.statusIcon);
            } else {
                holder.binding.participantStatusEmoji.setVisibility(View.GONE);
            }

            if (participant.status != null && participant.status.equals(StatusType.DND.getString())) {
                if (participant.statusMessage == null || participant.statusMessage.isEmpty()) {
                    holder.binding.conversationInfoStatusMessage.setText(R.string.dnd);
                }
            } else if (participant.status != null && participant.status.equals(StatusType.AWAY.getString())) {
                if (participant.statusMessage == null || participant.statusMessage.isEmpty()) {
                    holder.binding.conversationInfoStatusMessage.setText(R.string.away);
                }
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
        return participant.getDisplayName() != null &&
            (Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                .matcher(participant.getDisplayName().trim()).find() ||
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                    .matcher(participant.getActorId().trim()).find());
    }

    static class ParticipantItemViewHolder extends FlexibleViewHolder {

        RvItemConversationInfoParticipantBinding binding;

        /**
         * Default constructor.
         */
        ParticipantItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            binding = RvItemConversationInfoParticipantBinding.bind(view);
        }
    }
}
