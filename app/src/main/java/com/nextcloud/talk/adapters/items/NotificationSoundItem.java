/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <infoi@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.items;

import android.view.View;

import com.nextcloud.talk.R;
import com.nextcloud.talk.databinding.RvItemNotificationSoundBinding;

import java.util.List;
import java.util.Objects;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;

public class NotificationSoundItem extends AbstractFlexibleItem<NotificationSoundItem.NotificationSoundItemViewHolder> {

    private final String notificationSoundName;
    private final String notificationSoundUri;

    public NotificationSoundItem(String notificationSoundName, String notificationSoundUri) {
        this.notificationSoundName = notificationSoundName;
        this.notificationSoundUri = notificationSoundUri;
    }

    public String getNotificationSoundUri() {
        return notificationSoundUri;
    }

    public String getNotificationSoundName() {
        return notificationSoundName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        NotificationSoundItem that = (NotificationSoundItem) o;

        if (!Objects.equals(notificationSoundName, that.notificationSoundName)) {
            return false;
        }
        return Objects.equals(notificationSoundUri, that.notificationSoundUri);
    }

    @Override
    public int hashCode() {
        int result = notificationSoundName != null ? notificationSoundName.hashCode() : 0;
        return 31 * result + (notificationSoundUri != null ? notificationSoundUri.hashCode() : 0);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_notification_sound;
    }

    @Override
    public NotificationSoundItemViewHolder createViewHolder(View view, FlexibleAdapter<IFlexible> adapter) {
        return new NotificationSoundItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter,
                               NotificationSoundItemViewHolder holder,
                               int position,
                               List<Object> payloads) {
        holder.binding.notificationNameTextView.setText(notificationSoundName);
        holder.binding.notificationNameTextView.setChecked(adapter.isSelected(position));
    }

    static class NotificationSoundItemViewHolder extends FlexibleViewHolder {

        RvItemNotificationSoundBinding binding;

        /**
         * Default constructor.
         */
        NotificationSoundItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            binding = RvItemNotificationSoundBinding.bind(view);
        }
    }
}
