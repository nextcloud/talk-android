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

import android.util.Log;
import android.view.View;
import android.widget.TextView;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.nextcloud.talk.R;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractHeaderItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;

import java.util.List;

public class GenericTextHeaderItem extends AbstractHeaderItem<GenericTextHeaderItem.HeaderViewHolder> {
    private static final String TAG = "GenericTextHeaderItem";

    private String title;

    public GenericTextHeaderItem(String title) {
        super();
        setHidden(false);
        setSelectable(false);
        this.title = title;
    }

    public String getModel() {
        return title;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof GenericTextHeaderItem) {
            GenericTextHeaderItem inItem = (GenericTextHeaderItem) o;
            return title.equals(inItem.getModel());
        }
        return false;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_title_header;
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, HeaderViewHolder holder, int position, List<Object> payloads) {
        if (payloads.size() > 0) {
            Log.d(TAG, "We have payloads, so ignoring!");
        } else {
            holder.titleTextView.setText(title);
        }

    }

    @Override
    public HeaderViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new HeaderViewHolder(view, adapter);
    }

    static class HeaderViewHolder extends FlexibleViewHolder {

        @BindView(R.id.title_text_view)
        public TextView titleTextView;

        /**
         * Default constructor.
         */
        HeaderViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter, true);
            ButterKnife.bind(this, view);
        }
    }

}
