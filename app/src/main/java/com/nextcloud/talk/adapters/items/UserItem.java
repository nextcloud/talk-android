/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

import android.content.res.Resources;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.glide.GlideApp;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import javax.annotation.Nullable;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.ISectionable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.flipview.FlipView;
import eu.davidea.viewholders.FlexibleViewHolder;

public class UserItem extends AbstractFlexibleItem<UserItem.UserItemViewHolder> implements
        ISectionable<UserItem.UserItemViewHolder, GenericTextHeaderItem>, IFilterable<String> {

    private Participant participant;
    private UserEntity userEntity;
    private GenericTextHeaderItem header;

    private FlipView flipView;


    public UserItem(Participant participant, UserEntity userEntity, GenericTextHeaderItem genericTextHeaderItem) {
        this.participant = participant;
        this.userEntity = userEntity;
        this.header = genericTextHeaderItem;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UserItem) {
            UserItem inItem = (UserItem) o;
            return participant.equals(inItem.getModel());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return participant.hashCode();
    }

    /**
     * @return the model object
     */

    public Participant getModel() {
        return participant;
    }

    public UserEntity getEntity() {
        return userEntity;
    }

    public void flipItemSelection() {
        flipView.flip(!flipView.isFlipped());
    }

    @Override
    public int getLayoutRes() {
        if (header != null) {
            return R.layout.rv_item_contact;
        } else {
            return R.layout.rv_item_conversation_info_participant;
        }
    }

    @Override
    public UserItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new UserItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter adapter, UserItemViewHolder holder, int position, List payloads) {

        flipView = holder.avatarFlipView;

        flipView.flipSilently(adapter.isSelected(position));

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.contactDisplayName, participant.getDisplayName(),
                    String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.getSharedApplication()
                            .getResources().getColor(R.color.colorPrimary));
        } else {
            holder.contactDisplayName.setText(participant.getDisplayName());

            if (TextUtils.isEmpty(participant.getDisplayName()) &&
                    (participant.getType().equals(Participant.ParticipantType.GUEST) || participant.getType().equals(Participant.ParticipantType.USER_FOLLOWING_LINK))) {
                holder.contactDisplayName.setText(NextcloudTalkApplication.getSharedApplication().getString(R.string.nc_guest));
            }
        }

        int avatarSize = Math.round(NextcloudTalkApplication
                .getSharedApplication().getResources().getDimension(R.dimen.avatar_size));

        if (TextUtils.isEmpty(participant.getSource()) || participant.getSource().equals("users")) {

            if (Participant.ParticipantType.GUEST.equals(participant.getType()) ||
                    Participant.ParticipantType.USER_FOLLOWING_LINK.equals(participant.getType())) {
                // TODO: Show generated avatar for guests
            } else {
                GlideUrl glideUrl = new GlideUrl(ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                        participant.getUserId(), R.dimen.avatar_size), new LazyHeaders.Builder()
                        .setHeader("Accept", "image/*")
                        .setHeader("User-Agent", ApiUtils.getUserAgent())
                        .build());

                GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .load(glideUrl)
                        .centerInside()
                        .override(avatarSize, avatarSize)
                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                        .into(flipView.getFrontImageView());
            }
        } else if ("groups".equals(participant.getSource())) {

            GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                    .asBitmap()
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .load(R.drawable.ic_people_group_white_24px)
                    .centerInside()
                    .override(avatarSize, avatarSize)
                    .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                    .into(flipView.getFrontImageView());
        }

        if (!isEnabled()) {
            holder.itemView.setAlpha(0.38f);
        } else {
            holder.itemView.setAlpha(1.0f);
        }

        Resources resources = NextcloudTalkApplication.getSharedApplication().getResources();

        if (header == null) {
            Participant.ParticipantFlags participantFlags = participant.getParticipantFlags();
            switch (participantFlags) {
                case NOT_IN_CALL:
                    holder.voiceOrSimpleCallImageView.setVisibility(View.GONE);
                    holder.videoCallImageView.setVisibility(View.GONE);
                    break;
                case IN_CALL:
                    holder.voiceOrSimpleCallImageView.setBackground(resources.getDrawable(R.drawable.shape_call_bubble));
                    holder.voiceOrSimpleCallImageView.setVisibility(View.VISIBLE);
                    holder.videoCallImageView.setVisibility(View.GONE);
                    break;
                case IN_CALL_WITH_AUDIO:
                    holder.voiceOrSimpleCallImageView.setBackground(resources.getDrawable(R.drawable.shape_voice_bubble));
                    holder.voiceOrSimpleCallImageView.setVisibility(View.VISIBLE);
                    holder.videoCallImageView.setVisibility(View.GONE);
                    break;
                case IN_CALL_WITH_VIDEO:
                    holder.voiceOrSimpleCallImageView.setBackground(resources.getDrawable(R.drawable.shape_call_bubble));
                    holder.videoCallImageView.setBackground(resources.getDrawable(R.drawable.shape_video_bubble));
                    holder.voiceOrSimpleCallImageView.setVisibility(View.VISIBLE);
                    holder.videoCallImageView.setVisibility(View.VISIBLE);
                    break;
                case IN_CALL_WITH_AUDIO_AND_VIDEO:
                    holder.voiceOrSimpleCallImageView.setBackground(resources.getDrawable(R.drawable.shape_voice_bubble));
                    holder.videoCallImageView.setBackground(resources.getDrawable(R.drawable.shape_video_bubble));
                    holder.voiceOrSimpleCallImageView.setVisibility(View.VISIBLE);
                    holder.videoCallImageView.setVisibility(View.VISIBLE);
                    break;
                default:
                    holder.voiceOrSimpleCallImageView.setVisibility(View.GONE);
                    holder.videoCallImageView.setVisibility(View.GONE);
            }
        }

        String userType = "";

        if (header == null) {
            switch (new EnumParticipantTypeConverter().convertToInt(participant.getType())) {
                case 1:
                    userType = NextcloudTalkApplication.getSharedApplication().getString(R.string.nc_owner);
                    break;
                case 2:
                    userType = NextcloudTalkApplication.getSharedApplication().getString(R.string.nc_moderator);
                    break;
                case 3:
                    userType = NextcloudTalkApplication.getSharedApplication().getString(R.string.nc_user);
                    break;
                case 4:
                    userType = NextcloudTalkApplication.getSharedApplication().getString(R.string.nc_guest);
                    break;
                case 5:
                    userType = NextcloudTalkApplication.getSharedApplication().getString(R.string.nc_following_link);
                    break;
                default:
                    // do nothing
            }

            holder.contactMentionId.setText(userType);
            holder.contactMentionId.setTextColor(NextcloudTalkApplication.getSharedApplication().getResources().getColor(R.color.colorPrimary));
        }
    }

    @Override
    public boolean filter(String constraint) {
        return participant.getDisplayName() != null &&
                StringUtils.containsIgnoreCase(participant.getDisplayName().trim(), constraint);
    }

    @Override
    public GenericTextHeaderItem getHeader() {
        return header;
    }

    @Override
    public void setHeader(GenericTextHeaderItem header) {
        this.header = header;
    }


    static class UserItemViewHolder extends FlexibleViewHolder {

        @BindView(R.id.name_text)
        public TextView contactDisplayName;
        @BindView(R.id.avatar_flip_view)
        public FlipView avatarFlipView;
        @Nullable
        @BindView(R.id.secondary_text)
        public TextView contactMentionId;
        @Nullable
        @BindView(R.id.voiceOrSimpleCallImageView)
        ImageView voiceOrSimpleCallImageView;
        @Nullable
        @BindView(R.id.videoCallImageView)
        ImageView videoCallImageView;

        /**
         * Default constructor.
         */
        UserItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }


}
