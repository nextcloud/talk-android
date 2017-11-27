/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
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


import android.view.View;
import android.widget.TextView;

import com.nextcloud.talk.R;
import com.nextcloud.talk.events.MenuItemClickEvent;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.viewholders.FlexibleViewHolder;

public class MenuItem extends AbstractFlexibleItem<MenuItem.MenuItemViewHolder>  {
    private String title;

    public MenuItem(String title) {
        this.title = title;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MenuItem) {
            MenuItem inItem = (MenuItem) o;
            return title.equals(inItem.getModel());
        }
        return false;
    }

    private String getModel() {
        return title;
    }

    @Override
    public int getLayoutRes() {
        return R.layout.rv_item_menu;
    }

    @Override
    public MenuItem.MenuItemViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new MenuItemViewHolder(view, adapter);
    }

    @Override
    public void bindViewHolder(FlexibleAdapter adapter, MenuItem.MenuItemViewHolder holder, int position, List payloads) {
        holder.menuTitle.setText(title);

        holder.menuTitle.setOnClickListener(view -> EventBus.getDefault().post(new MenuItemClickEvent(title)));
    }

    static class MenuItemViewHolder extends FlexibleViewHolder {

        @BindView(R.id.menu_text)
        public TextView menuTitle;

        /**
         * Default constructor.
         */
        MenuItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }
}
