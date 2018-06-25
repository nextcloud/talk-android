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

import android.view.View;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.glide.GlideApp;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;

public class MentionAutocompleteItem extends AbstractFlexibleItem<UserItem.UserItemViewHolder>
        implements IFilterable<String> {

    private String userId;
    private String displayName;
    private UserEntity currentUser;

    public MentionAutocompleteItem(String userId, String displayName, UserEntity currentUser) {
        this.userId = userId;
        this.displayName = displayName;
        this.currentUser = currentUser;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MentionAutocompleteItem) {
            MentionAutocompleteItem inItem = (MentionAutocompleteItem) o;
            return (userId.equals(inItem.userId) && displayName.equals(inItem.displayName));
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


    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, UserItem.UserItemViewHolder holder, int position, List<Object> payloads) {

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.contactDisplayName, displayName,
                    String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.getSharedApplication()
                            .getResources().getColor(R.color.colorPrimary));
            FlexibleUtils.highlightText(holder.contactMentionId, "@" + userId,
                    String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.getSharedApplication()
                            .getResources().getColor(R.color.colorPrimary));
        } else {
            holder.contactDisplayName.setText(displayName);
            holder.contactMentionId.setText("@" + userId);
        }

        GlideUrl glideUrl = new GlideUrl(ApiUtils.getUrlForAvatarWithName(currentUser.getBaseUrl(),
                userId, R.dimen.avatar_size), new LazyHeaders.Builder()
                .setHeader("Accept", "image/*")
                .setHeader("User-Agent", ApiUtils.getUserAgent())
                .build());

        int avatarSize = Math.round(NextcloudTalkApplication
                .getSharedApplication().getResources().getDimension(R.dimen.avatar_size));

        GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                .asBitmap()
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .load(glideUrl)
                .centerInside()
                .override(avatarSize, avatarSize)
                .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                .into(holder.avatarFlipView.getFrontImageView());
    }

    @Override
    public boolean filter(String constraint) {
        return userId != null && StringUtils.containsIgnoreCase(userId, constraint) ||
                displayName != null && StringUtils.containsIgnoreCase(displayName, constraint);

    }
}
