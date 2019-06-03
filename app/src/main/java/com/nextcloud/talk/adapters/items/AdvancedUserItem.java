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

import android.accounts.Account;
import android.view.View;
import android.widget.*;
import androidx.annotation.Nullable;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

import java.util.List;
import java.util.regex.Pattern;

public class AdvancedUserItem extends AbstractFlexibleItem<AdvancedUserItem.UserItemViewHolder> implements
        IFilterable<String> {

    private Participant participant;
    private UserEntity userEntity;
    @Nullable
    private Account account;

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
        return R.layout.rv_item_conversation;
    }

    @Override
    public UserItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new UserItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter adapter, UserItemViewHolder holder, int position, List payloads) {
        holder.avatarImageView.setController(null);

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.contactDisplayName, participant.getName(),
                    String.valueOf(adapter.getFilter(String.class)), NextcloudTalkApplication.getSharedApplication()
                            .getResources().getColor(R.color.colorPrimary));
        } else {
            holder.contactDisplayName.setText(participant.getName());
        }

        holder.serverUrl.setText(userEntity.getBaseUrl());

        if (userEntity != null && userEntity.getBaseUrl() != null && userEntity.getBaseUrl().startsWith("http://") || userEntity.getBaseUrl().startsWith("https://")) {
            holder.avatarImageView.setVisibility(View.VISIBLE);

            DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                    .setOldController(holder.avatarImageView.getController())
                    .setAutoPlayAnimations(true)
                    .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                            participant.getUserId(), R.dimen.avatar_size), null))
                    .build();
            holder.avatarImageView.setController(draweeController);

        } else {
            holder.avatarImageView.setVisibility(View.GONE);
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) holder.linearLayout.getLayoutParams();
            layoutParams.setMarginStart((int) NextcloudTalkApplication.getSharedApplication().getApplicationContext()
                    .getResources().getDimension(R.dimen.activity_horizontal_margin));
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            holder.linearLayout.setLayoutParams(layoutParams);
        }
    }

    @Override
    public boolean filter(String constraint) {
        return participant.getName() != null &&
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL).matcher(participant.getName().trim()).find();
    }


    static class UserItemViewHolder extends FlexibleViewHolder {

        @BindView(R.id.name_text)
        public TextView contactDisplayName;
        @BindView(R.id.secondary_text)
        public TextView serverUrl;
        @BindView(R.id.avatar_image)
        public SimpleDraweeView avatarImageView;
        @BindView(R.id.linear_layout)
        LinearLayout linearLayout;
        @BindView(R.id.more_menu)
        ImageButton moreMenuButton;
        @BindView(R.id.password_protected_image_view)
        ImageView passwordProtectedImageView;

        /**
         * Default constructor.
         */
        UserItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
            moreMenuButton.setVisibility(View.GONE);
            passwordProtectedImageView.setVisibility(View.GONE);
        }
    }
}
