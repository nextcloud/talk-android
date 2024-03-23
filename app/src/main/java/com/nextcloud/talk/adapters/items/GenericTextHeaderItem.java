/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Andy Scherzinger <infoi@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2017-2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters.items;

import android.util.Log;
import android.view.View;

import com.nextcloud.talk.R;
import com.nextcloud.talk.databinding.RvItemTitleHeaderBinding;
import com.nextcloud.talk.ui.theme.ViewThemeUtils;

import java.util.List;
import java.util.Objects;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractHeaderItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.viewholders.FlexibleViewHolder;

public class GenericTextHeaderItem extends AbstractHeaderItem<GenericTextHeaderItem.HeaderViewHolder> {
    private static final String TAG = "GenericTextHeaderItem";

    private final String title;
    private final ViewThemeUtils viewThemeUtils;

    public GenericTextHeaderItem(String title, ViewThemeUtils viewThemeUtils) {
        super();
        setHidden(false);
        setSelectable(false);
        this.title = title;
        this.viewThemeUtils = viewThemeUtils;
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
    public int hashCode() {
        return Objects.hash(title);
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
            holder.binding.titleTextView.setText(title);
            viewThemeUtils.platform.colorPrimaryTextViewElement(holder.binding.titleTextView);
        }
    }

    @Override
    public HeaderViewHolder createViewHolder(View view, FlexibleAdapter adapter) {
        return new HeaderViewHolder(view, adapter);
    }

    static class HeaderViewHolder extends FlexibleViewHolder {

        RvItemTitleHeaderBinding binding;

        /**
         * Default constructor.
         */
        HeaderViewHolder(View view, FlexibleAdapter adapter) {
            super(view, adapter, true);
            binding = RvItemTitleHeaderBinding.bind(view);
        }
    }
}
