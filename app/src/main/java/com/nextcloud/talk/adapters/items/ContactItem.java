/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Marcel Hibbe
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2022 Marcel Hibbe <dev@mhibbe.de>
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

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.view.View;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.databinding.RvItemContactBinding;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;

import java.util.List;
import java.util.regex.Pattern;

import androidx.core.content.res.ResourcesCompat;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFilterable;
import eu.davidea.flexibleadapter.items.ISectionable;
import eu.davidea.flexibleadapter.utils.FlexibleUtils;
import eu.davidea.viewholders.FlexibleViewHolder;

public class ContactItem extends AbstractFlexibleItem<ContactItem.ContactItemViewHolder> implements
    ISectionable<ContactItem.ContactItemViewHolder, GenericTextHeaderItem>, IFilterable<String> {

    public static final String PARTICIPANT_SOURCE_CIRCLES = "circles";
    public static final String PARTICIPANT_SOURCE_GROUPS = "groups";
    public static final String PARTICIPANT_SOURCE_USERS = "users";

    private final Participant participant;
    private final UserEntity userEntity;
    private GenericTextHeaderItem header;
    public boolean isOnline = true;

    public ContactItem(Participant participant,
                       UserEntity userEntity,
                       GenericTextHeaderItem genericTextHeaderItem) {
        this.participant = participant;
        this.userEntity = userEntity;
        this.header = genericTextHeaderItem;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ContactItem) {
            ContactItem inItem = (ContactItem) o;
            return participant.getActorType() == inItem.getModel().getActorType() &&
                participant.getActorId().equals(inItem.getModel().getActorId());
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


    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_contact;
    }

    @Override
    public ContactItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new ContactItemViewHolder(view, adapter);
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bindViewHolder(FlexibleAdapter adapter, ContactItemViewHolder holder, int position, List payloads) {
        holder.binding.avatarDraweeView.setController(null);

        if (participant.isSelected()) {
            holder.binding.checkedImageView.setVisibility(View.VISIBLE);
        } else {
            holder.binding.checkedImageView.setVisibility(View.GONE);
        }

        if (!isOnline) {
            holder.binding.nameText.setTextColor(ResourcesCompat.getColor(
                holder.binding.nameText.getContext().getResources(),
                R.color.medium_emphasis_text,
                null)
                                                );
            holder.binding.avatarDraweeView.setAlpha(0.38f);
        } else {
            holder.binding.nameText.setTextColor(ResourcesCompat.getColor(
                holder.binding.nameText.getContext().getResources(),
                R.color.high_emphasis_text,
                null)
                                                );
            holder.binding.avatarDraweeView.setAlpha(1.0f);
        }

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.binding.nameText,
                                        participant.getDisplayName(),
                                        String.valueOf(adapter.getFilter(String.class)),
                                        NextcloudTalkApplication
                                            .Companion
                                            .getSharedApplication()
                                            .getResources()
                                            .getColor(R.color.colorPrimary));
        }

        holder.binding.nameText.setText(participant.getDisplayName());

        if (TextUtils.isEmpty(participant.getDisplayName()) &&
            (participant.getType().equals(Participant.ParticipantType.GUEST) ||
                participant.getType().equals(Participant.ParticipantType.USER_FOLLOWING_LINK))) {
            holder.binding.nameText.setText(NextcloudTalkApplication
                                                .Companion
                                                .getSharedApplication()
                                                .getString(R.string.nc_guest));
        }

        if (
            participant.getActorType() == Participant.ActorType.GROUPS ||
                PARTICIPANT_SOURCE_GROUPS.equals(participant.getSource()) ||
                participant.getActorType() == Participant.ActorType.CIRCLES ||
                PARTICIPANT_SOURCE_CIRCLES.equals(participant.getSource())) {

            holder.binding.avatarDraweeView.setImageResource(R.drawable.ic_circular_group);

        } else if (participant.getActorType() == Participant.ActorType.EMAILS) {

            holder.binding.avatarDraweeView.setImageResource(R.drawable.ic_circular_mail);

        } else if (
            participant.getActorType() == Participant.ActorType.GUESTS ||
                Participant.ParticipantType.GUEST.equals(participant.getType()) ||
                Participant.ParticipantType.GUEST_MODERATOR.equals(participant.getType())) {

            String displayName = NextcloudTalkApplication.Companion.getSharedApplication()
                .getResources().getString(R.string.nc_guest);

            if (!TextUtils.isEmpty(participant.getDisplayName())) {
                displayName = participant.getDisplayName();
            }

            DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(holder.binding.avatarDraweeView.getController())
                .setAutoPlayAnimations(true)
                .setImageRequest(DisplayUtils.getImageRequestForUrl(
                    ApiUtils.getUrlForGuestAvatar(userEntity.getBaseUrl(),
                                                  displayName,
                                                  false),
                    null))
                .build();
            holder.binding.avatarDraweeView.setController(draweeController);

        } else if (participant.getActorType() == Participant.ActorType.USERS ||
            PARTICIPANT_SOURCE_USERS.equals(participant.getSource())) {
            DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                .setOldController(holder.binding.avatarDraweeView.getController())
                .setAutoPlayAnimations(true)
                .setImageRequest(DisplayUtils.getImageRequestForUrl(
                    ApiUtils.getUrlForAvatar(userEntity.getBaseUrl(),
                                             participant.getActorId(),
                                             false),
                    null))
                .build();
            holder.binding.avatarDraweeView.setController(draweeController);
        }
    }

    @Override
    public boolean filter(String constraint) {
        return participant.getDisplayName() != null &&
            (Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                .matcher(participant.getDisplayName().trim())
                .find() ||
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                    .matcher(participant.getActorId().trim())
                    .find());
    }

    @Override
    public GenericTextHeaderItem getHeader() {
        return header;
    }

    @Override
    public void setHeader(GenericTextHeaderItem header) {
        this.header = header;
    }

    static class ContactItemViewHolder extends FlexibleViewHolder {

        RvItemContactBinding binding;

        /**
         * Default constructor.
         */
        ContactItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            binding = RvItemContactBinding.bind(view);
        }
    }
}
