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

import android.view.View;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.api.models.json.participants.Participant;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.persistence.entities.UserEntity;
import com.nextcloud.talk.utils.ColorUtils;
import com.nextcloud.talk.utils.glide.GlideApp;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import cn.carbs.android.avatarimageview.library.AvatarImageView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

public class UserItem extends AbstractFlexibleItem<UserItem.UserItemViewHolder> implements IFilterable {

    private Participant participant;
    private UserEntity userEntity;

    public UserItem(Participant participant, UserEntity userEntity) {
        this.participant = participant;
        this.userEntity = userEntity;
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

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_contact;
    }

    @Override
    public UserItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new UserItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter adapter, UserItemViewHolder holder, int position, List payloads) {
        if (adapter.hasSearchText()) {
            FlexibleUtils.highlightText(holder.contactDisplayName, participant.getName(), adapter.getSearchText());
        } else {
            holder.contactDisplayName.setText(participant.getName());
        }

        // Awful hack
        holder.avatarImageView.setTextAndColorSeed(String.valueOf(participant.getName().
                toUpperCase().charAt(0)), ColorUtils.colorSeed);

        GlideUrl glideUrl = new GlideUrl(ApiHelper.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                participant.getUserId()), new LazyHeaders.Builder()
                .setHeader("Accept", "image/*")
                .setHeader("User-Agent", ApiHelper.getUserAgent())
                .build());

        GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                .asBitmap()
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .load(glideUrl)
                .circleCrop()
                .centerInside()
                .into(holder.avatarImageView);
    }

    @Override
    public boolean filter(String constraint) {
        return participant.getName() != null &&
                StringUtils.containsIgnoreCase(participant.getName().trim(), constraint);
    }


    static class UserItemViewHolder extends FlexibleViewHolder {

        @BindView(R.id.name_text)
        public TextView contactDisplayName;
        @BindView(R.id.avatar_image)
        public AvatarImageView avatarImageView;

        /**
         * Default constructor.
         */
        UserItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }


}
