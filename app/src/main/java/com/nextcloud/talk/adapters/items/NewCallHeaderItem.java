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
import android.widget.RelativeLayout;

import com.nextcloud.talk.R;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractHeaderItem;
import eu.davidea.flexibleadapter.items.IHeader;
import eu.davidea.viewholders.FlexibleViewHolder;

public class NewCallHeaderItem extends AbstractHeaderItem<NewCallHeaderItem.HeaderViewHolder>
        implements IHeader<NewCallHeaderItem.HeaderViewHolder> {
    private static final String TAG = "NewCallHeaderItem";

    HeaderViewHolder headerViewHolder;

    public NewCallHeaderItem() {
        super();
        setSelectable(true);
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_call_header;
    }

    @Override
    public HeaderViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        headerViewHolder = new HeaderViewHolder(view, adapter);
        return headerViewHolder;
    }

    @Override
    public void bindViewHolder(FlexibleAdapter adapter, HeaderViewHolder holder, int position, List<Object> payloads) {
        headerViewHolder = holder;

        if (holder.secondaryRelativeLayout.getVisibility() == View.GONE && adapter.isSelected(position)) {
            togglePublicCall(true);
        } else if (holder.initialRelativeLayout.getVisibility() == View.GONE && !adapter.isSelected(position)) {
            togglePublicCall(false);
        }
    }

    public void togglePublicCall(boolean showDescription) {
        if (!showDescription) {
            headerViewHolder.secondaryRelativeLayout.setVisibility(View.GONE);
            headerViewHolder.initialRelativeLayout.setVisibility(View.VISIBLE);
        } else {
            headerViewHolder.initialRelativeLayout.setVisibility(View.GONE);
            headerViewHolder.secondaryRelativeLayout.setVisibility(View.VISIBLE);
        }
    }

    static class HeaderViewHolder extends FlexibleViewHolder {

        @BindView(R.id.initial_relative_layout)
        public RelativeLayout initialRelativeLayout;

        @BindView(R.id.secondary_relative_layout)
        public RelativeLayout secondaryRelativeLayout;

        /**
         * Default constructor.
         */
        HeaderViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter, true);
            ButterKnife.bind(this, view);
        }
    }
}
