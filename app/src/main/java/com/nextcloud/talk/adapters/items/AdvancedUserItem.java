/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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

import android.accounts.Account;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;

import com.nextcloud.talk.R;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.databinding.AccountItemBinding;
import com.nextcloud.talk.extensions.ImageViewExtensionsKt;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;

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
    private final User user;
    @Nullable
    private final Account account;
    private final ViewThemeUtils viewThemeUtils;

    public AdvancedUserItem(Participant participant,
                            User user,
                            @Nullable Account account,
                            ViewThemeUtils viewThemeUtils) {
        this.participant = participant;
        this.user = user;
        this.account = account;
        this.viewThemeUtils = viewThemeUtils;
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

    public User getUser() {
        return user;
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

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(
                    holder.binding.userName,
                    participant.getDisplayName(),
                    String.valueOf(adapter.getFilter(String.class)),
                    viewThemeUtils.getScheme(holder.binding.userName.getContext()).getPrimary());
        } else {
            holder.binding.userName.setText(participant.getDisplayName());
        }

        if (user != null && !TextUtils.isEmpty(user.getBaseUrl())) {
            String host = Uri.parse(user.getBaseUrl()).getHost();
            if (!TextUtils.isEmpty(host)) {
                holder.binding.account.setText(Uri.parse(user.getBaseUrl()).getHost());
            } else {
                holder.binding.account.setText(user.getBaseUrl());
            }
        }

        if (user != null && user.getBaseUrl() != null &&
                user.getBaseUrl().startsWith("http://") ||
                user.getBaseUrl().startsWith("https://")) {
            ImageViewExtensionsKt.loadAvatar(holder.binding.userIcon, user, participant.getCalculatedActorId(), true);
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
