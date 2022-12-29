/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2022 Andy Scherzinger <info@andy-scherzinger.de>
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
