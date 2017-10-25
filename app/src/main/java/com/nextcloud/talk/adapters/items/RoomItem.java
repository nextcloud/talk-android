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

import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.TextView;

import com.bumptech.glide.load.model.GlideUrl;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.api.models.json.rooms.Room;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.persistence.entities.UserEntity;
import com.nextcloud.talk.utils.ColorUtils;
import com.nextcloud.talk.utils.GlideApp;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import cn.carbs.android.avatarimageview.library.AvatarImageView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

public class RoomItem extends AbstractFlexibleItem<RoomItem.RoomItemViewHolder> implements IFilterable {

    private Room room;
    private UserEntity userEntity;

    public RoomItem(Room room, UserEntity userEntity) {
        this.room = room;
        this.userEntity = userEntity;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof RoomItem) {
            RoomItem inItem = (RoomItem) o;
            return room.equals(inItem.getModel());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return room.hashCode();
    }

    /**
     * @return the model object
     */

    public Room getModel() {
        return room;
    }

    /**
     * Filter is applied to the model fields.
     */

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_call;
    }

    @Override
    public RoomItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new RoomItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(final FlexibleAdapter adapter, RoomItemViewHolder holder, int position, List payloads) {
        if (adapter.hasSearchText()) {
            FlexibleUtils.highlightText(holder.roomDisplayName, room.getDisplayName(), adapter.getSearchText());
        } else {
            holder.roomDisplayName.setText(room.getDisplayName());
        }

        if (room.getLastPing() == 0) {
            holder.roomLastPing.setText(R.string.nc_never);
        } else {
            holder.roomLastPing.setText(DateUtils.getRelativeTimeSpanString(room.getLastPing() * 1000L,
                    System.currentTimeMillis(), 0, DateUtils.FORMAT_ABBREV_RELATIVE));
        }


        switch (room.getType()) {
            case ROOM_TYPE_ONE_TO_ONE_CALL:
                holder.avatarImageView.setVisibility(View.VISIBLE);

                holder.avatarImageViewInvisible.setTextAndColorSeed(String.valueOf(room.getDisplayName().
                        toUpperCase().charAt(0)), ColorUtils.colorSeed);

                if (!TextUtils.isEmpty(room.getName())) {
                    String glideUrl = new GlideUrl(ApiHelper.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                            room.getName())).toString();

                    GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                            .asBitmap()
                            .skipMemoryCache(true)
                            .load(glideUrl)
                            .placeholder(holder.avatarImageViewInvisible.getDrawable())
                            .circleCrop()
                            .centerInside()
                            .into(holder.avatarImageView);
                } else {
                    holder.avatarImageView.setDrawable(holder.avatarImageViewInvisible.getDrawable());
                }
                break;
            case ROOM_GROUP_CALL:
                holder.avatarImageView.setVisibility(View.VISIBLE);
                holder.avatarImageView.setImageResource(R.drawable.ic_group_black_24dp);
                break;
            case ROOM_PUBLIC_CALL:
                holder.avatarImageView.setVisibility(View.VISIBLE);
                holder.avatarImageView.setImageResource(R.drawable.ic_public_black_24dp);
                break;
            default:
                holder.avatarImageView.setVisibility(View.GONE);

        }
    }

    @Override
    public boolean filter(String constraint) {
        return room.getDisplayName() != null && room.getDisplayName().toLowerCase().trim().contains(
                constraint.toLowerCase());

    }

    static class RoomItemViewHolder extends FlexibleViewHolder {

        @BindView(R.id.name_text)
        public TextView roomDisplayName;
        @BindView(R.id.timestamp_text)
        public TextView roomLastPing;
        @BindView(R.id.avatar_image)
        public AvatarImageView avatarImageView;
        @BindView(R.id.avatar_image_invisible)
        public AvatarImageView avatarImageViewInvisible;

        /**
         * Default constructor.
         */
        RoomItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }
}
