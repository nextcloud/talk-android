/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.utils.ui;

import android.content.Context;
import android.content.res.TypedArray;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.nextcloud.talk.R;
import com.yarolegovich.mp.util.Utils;

import androidx.annotation.ColorInt;
import androidx.annotation.ColorRes;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

public class MaterialPreferenceCategoryWithRightLink extends CardView {

    private ViewGroup container;
    private TextView title;
    private TextView action;

    public MaterialPreferenceCategoryWithRightLink(Context context) {
        super(context);
        init(null);
    }

    public MaterialPreferenceCategoryWithRightLink(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);
    }

    public MaterialPreferenceCategoryWithRightLink(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(attrs);
    }

    private void init(AttributeSet attrs) {
        int titleColor = -1;
        String titleText = "";
        String actionText = "";
        if (attrs != null) {
            TypedArray ta = getContext().obtainStyledAttributes(attrs, R.styleable.MaterialPreferenceCategory);
            try {
                if (ta.hasValue(R.styleable.MaterialPreferenceCategory_mpc_title)) {
                    titleText = ta.getString(R.styleable.MaterialPreferenceCategory_mpc_title);
                }

                if (ta.hasValue(R.styleable.MaterialPreferenceCategory_mpc_action)) {
                    actionText = ta.getString(R.styleable.MaterialPreferenceCategory_mpc_action);
                }

                titleColor = ta.getColor(R.styleable.MaterialPreferenceCategory_mpc_title_color, -1);
            } finally {
                ta.recycle();
            }
        }

        inflate(getContext(), R.layout.category_with_right_action, this);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, Utils.dpToPixels(getContext(), 4));

        setUseCompatPadding(true);

        setRadius(0);

        container = (ViewGroup) findViewById(R.id.mpc_container);
        title = (TextView) findViewById(R.id.mpc_title);
        action = findViewById(R.id.mpc_action);

        if (!TextUtils.isEmpty(titleText)) {
            title.setVisibility(View.VISIBLE);
            title.setText(titleText);
        }

        if (!TextUtils.isEmpty(actionText)) {
            action.setVisibility(View.VISIBLE);
            action.setText(actionText);
        }

        if (titleColor != -1) {
            title.setTextColor(titleColor);
        }
    }

    public void setAction(String actionText) {
        action.setText(actionText);
    }

    public void setTitle(String titleText) {
        title.setVisibility(View.VISIBLE);
        title.setText(titleText);
    }

    public void setTitleColor(@ColorInt int color) {
        title.setTextColor(color);
    }

    public void setTitleColorRes(@ColorRes int colorRes) {
        title.setTextColor(ContextCompat.getColor(getContext(), colorRes));
    }

    @Override
    public void addView(View child) {
        if (container != null) {
            container.addView(child);
        } else {
            super.addView(child);
        }
    }

    @Override
    public void addView(View child, int index) {
        if (container != null) {
            container.addView(child, index);
        } else {
            super.addView(child, index);
        }
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (container != null) {
            container.addView(child, params);
        } else {
            super.addView(child, params);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        if (container != null) {
            container.addView(child, index, params);
        } else {
            super.addView(child, index, params);
        }
    }

}
