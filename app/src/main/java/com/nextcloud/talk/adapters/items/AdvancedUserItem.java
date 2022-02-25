/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

import android.accounts.Account;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.databinding.AccountItemBinding;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;

import java.util.List;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

public class AdvancedUserItem extends AbstractFlexibleItem<AdvancedUserItem.UserItemViewHolder> implements
        IFilterable<String> {

    private final Participant participant;
    private final UserEntity userEntity;
    @Nullable
    private final Account account;

    public AdvancedUserItem(Participant participant, UserEntity userEntity, @Nullable Account account) {
        this.participant = participant;
        this.userEntity = userEntity;
        this.account = account;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AdvancedUserItem) {
            AdvancedUserItem inItem = (AdvancedUserItem) o;
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

    @Nullable
    public Account getAccount() {
        return account;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.account_item;
    }

    @Override
    public UserItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new UserItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter adapter, UserItemViewHolder holder, int position, List payloads) {
        holder.binding.userIcon.setController(null);

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(
                    holder.binding.userName,
                    participant.getDisplayName(),
                    String.valueOf(adapter.getFilter(String.class)),
                    NextcloudTalkApplication.Companion.getSharedApplication()
                            .getResources()
                            .getColor(R.color.colorPrimary));
        } else {
            holder.binding.userName.setText(participant.getDisplayName());
        }

        if (userEntity != null && !TextUtils.isEmpty(userEntity.getBaseUrl())) {
            String host = Uri.parse(userEntity.getBaseUrl()).getHost();
            if (!TextUtils.isEmpty(host)) {
                holder.binding.account.setText(Uri.parse(userEntity.getBaseUrl()).getHost());
            } else {
                holder.binding.account.setText(userEntity.getBaseUrl());
            }
        }

        holder.binding.userIcon.getHierarchy().setPlaceholderImage(R.drawable.account_circle_48dp);
        holder.binding.userIcon.getHierarchy().setFailureImage(R.drawable.account_circle_48dp);

        if (userEntity != null && userEntity.getBaseUrl() != null &&
                userEntity.getBaseUrl().startsWith("http://") ||
                userEntity.getBaseUrl().startsWith("https://")) {

            DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(holder.binding.userIcon.getController())
                .setAutoPlayAnimations(true)
                .setImageRequest(
                    DisplayUtils.getImageRequestForUrl(
                        ApiUtils.getUrlForAvatarWithName(
                            userEntity.getBaseUrl(),
                            participant.getActorId(),
                            R.dimen.small_item_height),
                        null))
                .build();
            holder.binding.userIcon.setController(draweeController);
        }
    }

    @Override
    public boolean filter(String constraint) {
        return participant.getDisplayName() != null &&
                Pattern
                    .compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                    .matcher(participant.getDisplayName().trim())
                    .find();
    }

    static class UserItemViewHolder extends FlexibleViewHolder {

        public AccountItemBinding binding;

        /**
         * Default constructor.
         */
        UserItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            binding = AccountItemBinding.bind(view);
        }
    }
}
