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

import com.nextcloud.talk.R;

import java.util.List;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;

public class EmptyFooterItem extends AbstractFlexibleItem<EmptyFooterItem.EmptyFooterItemViewHolder> {
    int id;

    public EmptyFooterItem(int id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof EmptyFooterItem) {
            EmptyFooterItem inItem = (EmptyFooterItem) o;
            return id == inItem.getModel();
        }
        return false;
    }

    public int getModel() {
        return id;
    }


    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_empty_footer;
    }

    @Override
    public void bindViewHolder(FlexibleAdapter<IFlexible> adapter, EmptyFooterItemViewHolder holder,
                               int position, List<Object> payloads) {

    }

    @Override
    public EmptyFooterItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new EmptyFooterItemViewHolder(view, adapter);
    }

    static class EmptyFooterItemViewHolder extends FlexibleViewHolder {
        /**
         * Default constructor.
         */
        EmptyFooterItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
        }

    }
}
