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

package com.moyn.talk.adapters.items;

import android.accounts.Account;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.moyn.talk.R;
import com.moyn.talk.application.NextcloudTalkApplication;
import com.moyn.talk.models.database.UserEntity;
import com.moyn.talk.models.json.participants.Participant;
import com.moyn.talk.utils.ApiUtils;
import com.moyn.talk.utils.DisplayUtils;

import java.util.List;
import java.util.regex.Pattern;

import androidx.annotation.Nullable;
import androidx.emoji.widget.EmojiTextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

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
            FlexibleUtils.highlightText(
                    holder.contactDisplayName,
                    participant.getDisplayName(),
                    String.valueOf(adapter.getFilter(String.class)),
                    NextcloudTalkApplication.Companion.getSharedApplication()
                            .getResources()
                            .getColor(R.color.colorPrimary));
        } else {
            holder.contactDisplayName.setText(participant.getDisplayName());
        }

        if (userEntity != null && !TextUtils.isEmpty(userEntity.getBaseUrl())) {
            holder.serverUrl.setText((Uri.parse(userEntity.getBaseUrl()).getHost()));
        }

        holder.avatarImageView.getHierarchy().setPlaceholderImage(R.drawable.account_circle_48dp);
        holder.avatarImageView.getHierarchy().setFailureImage(R.drawable.account_circle_48dp);

        if (userEntity != null && userEntity.getBaseUrl() != null &&
                userEntity.getBaseUrl().startsWith("http://") ||
                userEntity.getBaseUrl().startsWith("https://")) {
            holder.avatarImageView.setVisibility(View.VISIBLE);

            DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                    .setOldController(holder.avatarImageView.getController())
                    .setAutoPlayAnimations(true)
                    .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                            participant.getActorId(), R.dimen.small_item_height), null))
                    .build();
            holder.avatarImageView.setController(draweeController);
        } else {
            holder.avatarImageView.setVisibility(View.GONE);
            RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams) holder.linearLayout.getLayoutParams();
            layoutParams.setMarginStart((int) NextcloudTalkApplication.Companion.getSharedApplication().getApplicationContext()
                    .getResources().getDimension(R.dimen.activity_horizontal_margin));
            layoutParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            holder.linearLayout.setLayoutParams(layoutParams);
        }
    }

    @Override
    public boolean filter(String constraint) {
        return participant.getDisplayName() != null &&
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL).matcher(participant.getDisplayName().trim()).find();
    }


    static class UserItemViewHolder extends FlexibleViewHolder {

        @BindView(R.id.name_text)
        public EmojiTextView contactDisplayName;
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
