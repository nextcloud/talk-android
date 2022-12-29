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
import android.os.Build;
import android.text.TextUtils;
import android.view.View;

import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.databinding.RvItemContactBinding;
import com.nextcloud.talk.extensions.ImageViewExtensionsKt;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;

import java.util.List;
import java.util.Objects;
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
    private final User user;
    private GenericTextHeaderItem header;
    private final ViewThemeUtils viewThemeUtils;
    public boolean isOnline = true;

    public ContactItem(Participant participant,
                       User user,
                       GenericTextHeaderItem genericTextHeaderItem,
                       ViewThemeUtils viewThemeUtils) {
        this.participant = participant;
        this.user = user;
        this.header = genericTextHeaderItem;
        this.viewThemeUtils = viewThemeUtils;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof ContactItem) {
            ContactItem inItem = (ContactItem) o;
            return participant.getCalculatedActorType() == inItem.getModel().getCalculatedActorType() &&
                participant.getCalculatedActorId().equals(inItem.getModel().getCalculatedActorId());
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

        if (participant.getSelected()) {
            viewThemeUtils.platform.colorImageView(holder.binding.checkedImageView);
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
            holder.binding.avatarView.setAlpha(0.38f);
        } else {
            holder.binding.nameText.setTextColor(ResourcesCompat.getColor(
                                                     holder.binding.nameText.getContext().getResources(),
                                                     R.color.high_emphasis_text,
                                                     null)
                                                );
            holder.binding.avatarView.setAlpha(1.0f);
        }

        holder.binding.nameText.setText(participant.getDisplayName());

        if (adapter.hasFilter()) {
            FlexibleUtils.highlightText(holder.binding.nameText,
                                        participant.getDisplayName(),
                                        String.valueOf(adapter.getFilter(String.class)),
                                        viewThemeUtils.getScheme(holder.binding.nameText.getContext()).getPrimary());
        }

        if (TextUtils.isEmpty(participant.getDisplayName()) &&
            (participant.getType() == Participant.ParticipantType.GUEST ||
                participant.getType() == Participant.ParticipantType.USER_FOLLOWING_LINK)) {
            holder.binding.nameText.setText(NextcloudTalkApplication
                                                .Companion
                                                .getSharedApplication()
                                                .getString(R.string.nc_guest));
        }

        if (
            participant.getCalculatedActorType() == Participant.ActorType.GROUPS ||
                PARTICIPANT_SOURCE_GROUPS.equals(participant.getSource()) ||
                participant.getCalculatedActorType() == Participant.ActorType.CIRCLES ||
                PARTICIPANT_SOURCE_CIRCLES.equals(participant.getSource())) {

            setGenericAvatar(holder, R.drawable.ic_avatar_group, R.drawable.ic_circular_group);

        } else if (participant.getCalculatedActorType() == Participant.ActorType.EMAILS) {

            setGenericAvatar(holder, R.drawable.ic_avatar_mail, R.drawable.ic_circular_mail);

        } else if (
            participant.getCalculatedActorType() == Participant.ActorType.GUESTS ||
                participant.getType() == Participant.ParticipantType.GUEST ||
                participant.getType() == Participant.ParticipantType.GUEST_MODERATOR) {

            String displayName;

            if (!TextUtils.isEmpty(participant.getDisplayName())) {
                displayName = participant.getDisplayName();
            } else {
                displayName = Objects.requireNonNull(NextcloudTalkApplication.Companion.getSharedApplication())
                    .getResources().getString(R.string.nc_guest);
            }

            // absolute fallback to prevent NPE deference
            if (displayName == null) {
                displayName = "Guest";
            }

            ImageViewExtensionsKt.loadAvatar(holder.binding.avatarView, user, displayName, true);
        } else if (participant.getCalculatedActorType() == Participant.ActorType.USERS ||
            PARTICIPANT_SOURCE_USERS.equals(participant.getSource())) {
            ImageViewExtensionsKt.loadAvatar(holder.binding.avatarView, user, participant.getCalculatedActorId(), true);
        }
    }

    private void setGenericAvatar(
        ContactItemViewHolder holder,
        int roundPlaceholderDrawable,
        int fallbackImageResource) {
        Object avatar;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            avatar = viewThemeUtils.talk.themePlaceholderAvatar(
                holder.binding.avatarView,
                roundPlaceholderDrawable
                                                               );

        } else {
            avatar = fallbackImageResource;
        }

        ImageViewExtensionsKt.loadAvatar(holder.binding.avatarView, avatar);
    }

    @Override
    public boolean filter(String constraint) {
        return participant.getDisplayName() != null &&
            (Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                .matcher(participant.getDisplayName().trim())
                .find() ||
                Pattern.compile(constraint, Pattern.CASE_INSENSITIVE | Pattern.LITERAL)
                    .matcher(participant.getCalculatedActorId().trim())
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
