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
import android.graphics.drawable.BitmapDrawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.converters.EnumParticipantTypeConverter;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;

import java.util.List;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import androidx.emoji.widget.EmojiTextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.ISectionable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

public class UserItem extends AbstractFlexibleItem<UserItem.UserItemViewHolder> implements
        ISectionable<UserItem.UserItemViewHolder, GenericTextHeaderItem>, IFilterable<String> {

    private Participant participant;
    private UserEntity userEntity;
    private GenericTextHeaderItem header;
    public boolean isOnline = true;

    public UserItem(Participant participant, UserEntity userEntity, GenericTextHeaderItem genericTextHeaderItem) {
        this.participant = participant;
        this.userEntity = userEntity;
        this.header = genericTextHeaderItem;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof UserItem) {
            UserItem inItem = (UserItem) o;
            return participant.getUserId().equals(inItem.getModel().getUserId());
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

        holder.simpleDraweeView.setController(null);

        if (holder.checkedImageView != null) {
            if (participant.isSelected()) {
                holder.checkedImageView.setVisibility(View.VISIBLE);
            } else {
                holder.checkedImageView.setVisibility(View.GONE);
            }
        }

        if (!isOnline) {
            if (holder.contactMentionId != null) {
                holder.contactMentionId.setAlpha(0.38f);
            }
            holder.contactDisplayName.setAlpha(0.38f);
            holder.simpleDraweeView.setAlpha(0.38f);
        } else {
            if (holder.contactMentionId != null) {
                holder.contactMentionId.setAlpha(1.0f);
            }
            holder.contactDisplayName.setAlpha(1.0f);
            holder.simpleDraweeView.setAlpha(1.0f);
        }

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.contactDisplayName, participant.getDisplayName(),
                    String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.Companion.getSharedApplication()
                            .getResources().getColor(R.color.colorPrimary));
        }

        holder.contactDisplayName.setText(participant.getDisplayName());

        if (TextUtils.isEmpty(participant.getDisplayName()) &&
                (participant.getType().equals(Participant.ParticipantType.GUEST) || participant.getType().equals(Participant.ParticipantType.USER_FOLLOWING_LINK))) {
            holder.contactDisplayName.setText(NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest));
        }

        if (TextUtils.isEmpty(participant.getSource()) || participant.getSource().equals("users")) {
            if (Participant.ParticipantType.GUEST.equals(participant.getType()) ||
                    Participant.ParticipantType.USER_FOLLOWING_LINK.equals(participant.getType())) {
                String displayName = NextcloudTalkApplication.Companion.getSharedApplication()
                        .getResources().getString(R.string.nc_guest);

                if (!TextUtils.isEmpty(participant.getDisplayName())) {
                    displayName = participant.getDisplayName();
                }

                DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                        .setOldController(holder.simpleDraweeView.getController())
                        .setAutoPlayAnimations(true)
                        .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithNameForGuests(userEntity.getBaseUrl(),
                                displayName, R.dimen.avatar_size), null))
                        .build();
                holder.simpleDraweeView.setController(draweeController);

            } else {

                DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                        .setOldController(holder.simpleDraweeView.getController())
                        .setAutoPlayAnimations(true)
                        .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                                participant.getUserId(), R.dimen.avatar_size), null))
                        .build();
                holder.simpleDraweeView.setController(draweeController);

            }
        } else if ("groups".equals(participant.getSource())) {
            holder.simpleDraweeView.getHierarchy().setImage(new BitmapDrawable(DisplayUtils.getRoundedBitmapFromVectorDrawableResource(NextcloudTalkApplication.Companion.getSharedApplication().getResources(), R.drawable.ic_people_group_white_24px)), 100, true);
        }

        Resources resources = NextcloudTalkApplication.Companion.getSharedApplication().getResources();

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
                    break;
            }


            if (holder.contactMentionId != null) {
                String userType = "";

                switch (new EnumParticipantTypeConverter().convertToInt(participant.getType())) {
                    case 1:
                        //userType = NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_owner);
                        //break;
                    case 2:
                        userType = NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_moderator);
                        break;
                    case 3:
                        userType = NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_user);
                        break;
                    case 4:
                        userType = NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_guest);
                        break;
                    case 5:
                        userType = NextcloudTalkApplication.Companion.getSharedApplication().getString(R.string.nc_following_link);
                        break;
                    default:
                        break;
                }

                if (!holder.contactMentionId.getText().equals(userType)) {
                    holder.contactMentionId.setText(userType);
                    holder.contactMentionId.setTextColor(NextcloudTalkApplication.Companion.getSharedApplication().getResources().getColor(R.color.textColorMaxContrast));
                }
            }
        }
    }

    @Override
    public boolean filter(String constraint) {
        return participant.getDisplayName() != null &&
                (Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL).matcher(participant.getDisplayName().trim()).find() ||
                        Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL).matcher(participant.getUserId().trim()).find());
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
        public EmojiTextView contactDisplayName;
        @BindView(R.id.simple_drawee_view)
        public SimpleDraweeView simpleDraweeView;
        @Nullable
        @BindView(R.id.secondary_text)
        public EmojiTextView contactMentionId;
        @Nullable
        @BindView(R.id.voiceOrSimpleCallImageView)
        ImageView voiceOrSimpleCallImageView;
        @Nullable
        @BindView(R.id.videoCallImageView)
        ImageView videoCallImageView;
        @Nullable
        @BindView(R.id.checkedImageView)
        ImageView checkedImageView;

        /**
         * Default constructor.
         */
        UserItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }


}
