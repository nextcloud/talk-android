/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
 * Copyright (C) 2018 Andy Scherzinger
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


import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import butterknife.BindView;
import butterknife.ButterKnife;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.utils.DisplayUtils;
import com.vanniktech.emoji.EmojiTextView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.viewholders.FlexibleViewHolder;

import java.util.List;

public class MenuItem extends AbstractFlexibleItem<MenuItem.MenuItemViewHolder> {
    private String title;
    private Drawable icon;
    private int tag;
    private int padding;

    public MenuItem(String title, int tag, Drawable icon) {
        this.title = title;
        this.tag = tag;
        this.icon = icon;
        padding = (int) DisplayUtils.convertDpToPixel(16,
                NextcloudTalkApplication.getSharedApplication().getApplicationContext());
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof MenuItem) {
            MenuItem inItem = (MenuItem) o;
            return tag == inItem.tag;
        }
        return false;
    }

    public int getTag() {
        return tag;
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
        if (position == 0) {
            Spannable spannableString = new SpannableString(title);
            spannableString.setSpan(new ForegroundColorSpan(NextcloudTalkApplication.getSharedApplication()
                            .getResources().getColor(R.color.grey_600)), 0,
                    spannableString.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            holder.menuTitle.setText(spannableString);
        } else {
            holder.menuTitle.setText(title);
            holder.menuTitle.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
            holder.menuTitle.setCompoundDrawablePadding(padding);
        }
    }

    static class MenuItemViewHolder extends FlexibleViewHolder {

        @BindView(R.id.menu_text)
        public EmojiTextView menuTitle;

        /**
         * Default constructor.
         */
        MenuItemViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter);
            ButterKnife.bind(this, view);
        }
    }
}
