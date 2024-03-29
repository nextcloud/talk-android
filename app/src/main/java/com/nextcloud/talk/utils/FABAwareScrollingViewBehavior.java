/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2015 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.nextcloud.talk.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

public class FABAwareScrollingViewBehavior extends AppBarLayout.ScrollingViewBehavior {

    public FABAwareScrollingViewBehavior(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public boolean layoutDependsOn(CoordinatorLayout parent, View child, View dependency) {
        return super.layoutDependsOn(parent, child, dependency) ||
                dependency instanceof FloatingActionButton;
    }

    @Override
    public boolean onStartNestedScroll(final CoordinatorLayout coordinatorLayout, final View child,
                                       final View directTargetChild, final View target, final int nestedScrollAxes) {
        // Ensure we react to vertical scrolling
        return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL
                || super.onStartNestedScroll(coordinatorLayout, child, directTargetChild, target, nestedScrollAxes);
    }

    @Override
    public void onNestedScroll(final CoordinatorLayout coordinatorLayout, final View child,
                               final View target, final int dxConsumed, final int dyConsumed,
                               final int dxUnconsumed, final int dyUnconsumed) {
        super.onNestedScroll(coordinatorLayout, child, target, dxConsumed, dyConsumed, dxUnconsumed, dyUnconsumed);
        if (dyConsumed > 0) {
            // User scrolled down -> hide the FAB
            List<View> dependencies = coordinatorLayout.getDependencies(child);
            for (View view : dependencies) {
                if (view instanceof FloatingActionButton) {
                    ((FloatingActionButton) view).hide();
                }
            }
        } else if (dyConsumed < 0) {
            // User scrolled up -> show the FAB
            List<View> dependencies = coordinatorLayout.getDependencies(child);
            for (View view : dependencies) {
                if (view instanceof FloatingActionButton) {
                    ((FloatingActionButton) view).show();
                }
            }
        }
    }
}