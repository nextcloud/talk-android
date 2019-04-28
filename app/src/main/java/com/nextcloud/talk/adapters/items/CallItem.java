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
import android.text.format.DateUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.events.MoreMenuClickEvent;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;
import org.greenrobot.eventbus.EventBus;

import java.util.List;
import java.util.regex.Pattern;

public class CallItem extends AbstractFlexibleItem<CallItem.RoomItemViewHolder> implements IFilterable<String> {

    private Conversation conversation;
    private UserEntity userEntity;

    public CallItem(Conversation conversation, UserEntity userEntity) {
        this.conversation = conversation;
        this.userEntity = userEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof CallItem) {
            CallItem inItem = (CallItem) o;
            return conversation.equals(inItem.getModel());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return conversation.hashCode();
    }

    /**
     * @return the model object
     */

    public Conversation getModel() {
        return conversation;
    }

    /**
     * Filter is applied to the model fields.
     */

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_conversation;
    }

    @Override
    public RoomItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new RoomItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(final FlexibleAdapter adapter, RoomItemViewHolder holder, int position, List payloads) {
        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.roomDisplayName, conversation.getDisplayName(),
                    String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.getSharedApplication()
                            .getResources().getColor(R.color.colorPrimary));
        } else {
            holder.roomDisplayName.setText(conversation.getDisplayName());
        }

        if (conversation.getLastPing() == 0) {
            holder.roomLastPing.setText(R.string.nc_never);
        } else {
            holder.roomLastPing.setText(DateUtils.getRelativeTimeSpanString(conversation.getLastPing() * 1000L,
                    System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE));
        }

        if (conversation.hasPassword) {
            holder.passwordProtectedImageView.setVisibility(View.VISIBLE);
        } else {
            holder.passwordProtectedImageView.setVisibility(View.GONE);
        }

        Resources resources = NextcloudTalkApplication.getSharedApplication().getResources();
        switch (conversation.getType()) {
            case ROOM_TYPE_ONE_TO_ONE_CALL:
                holder.avatarImageView.setVisibility(View.VISIBLE);

                holder.moreMenuButton.setContentDescription(String.format(resources.getString(R.string
                        .nc_description_more_menu_one_to_one), conversation.getDisplayName()));

                if (!TextUtils.isEmpty(conversation.getName())) {
                    DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                            .setOldController(holder.avatarImageView.getController())
                            .setAutoPlayAnimations(true)
                            .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                                    conversation.getName(),
                                    R.dimen.avatar_size), null))
                            .build();
                    holder.avatarImageView.setController(draweeController);
                } else {
                    holder.avatarImageView.setVisibility(View.GONE);
                }
                break;
            case ROOM_GROUP_CALL:
                holder.moreMenuButton.setContentDescription(String.format(resources.getString(R.string
                        .nc_description_more_menu_group), conversation.getDisplayName()));
                holder.avatarImageView.setActualImageResource(R.drawable.ic_people_group_white_24px);
                holder.avatarImageView.setVisibility(View.VISIBLE);
                break;
            case ROOM_PUBLIC_CALL:
                holder.moreMenuButton.setContentDescription(String.format(resources.getString(R.string
                        .nc_description_more_menu_public), conversation.getDisplayName()));
                holder.avatarImageView.setActualImageResource(R.drawable.ic_link_white_24px);
                holder.avatarImageView.setVisibility(View.VISIBLE);
                break;
            default:
                holder.avatarImageView.setVisibility(View.GONE);

        }

        holder.moreMenuButton.setOnClickListener(view -> EventBus.getDefault().post(new MoreMenuClickEvent(conversation)));
    }

    @Override
    public boolean filter(String constraint) {
        return conversation.getDisplayName() != null &&
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL).matcher(conversation.getDisplayName().trim()).find();
    }

    static class RoomItemViewHolder extends FlexibleViewHolder {

        @BindView(R.id.name_text)
        public TextView roomDisplayName;
        @BindView(R.id.secondary_text)
        public TextView roomLastPing;
        @BindView(R.id.avatar_image)
        public SimpleDraweeView avatarImageView;
        @BindView(R.id.more_menu)
        public ImageButton moreMenuButton;
        @BindView(R.id.password_protected_image_view)
        ImageView passwordProtectedImageView;

        RoomItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }
}
