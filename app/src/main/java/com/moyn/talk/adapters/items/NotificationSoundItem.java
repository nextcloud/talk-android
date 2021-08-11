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

package com.moyn.talk.adapters.items;

import android.view.View;

import com.google.android.material.radiobutton.MaterialRadioButton;
import com.moyn.talk.R;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
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
        return false;
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
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, NotificationSoundItemViewHolder holder, int position, List<Object> payloads) {
        holder.notificationName.setText(notificationSoundName);
        holder.notificationName.setChecked(adapter.isSelected(position));
    }

    static class NotificationSoundItemViewHolder extends FlexibleViewHolder {
        @BindView(R.id.notificationNameTextView)
        public MaterialRadioButton notificationName;

        /**
         * Default constructor.
         */
        NotificationSoundItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }
}
