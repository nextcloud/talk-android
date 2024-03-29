/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2018 Mario Danic <mario@lovelyhq.com>
 * SPDX-FileCopyrightText: 2016 Srijith Narayanan
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * The original code is Copyright 2016 Srijith Narayanan under MIT licence
 * https://github.com/sjthn/BottomNavigationViewBehavior/blob/9558104a16a1276bd8a73fba6736d88cd25b5488/app/src/main/java/com/example/srijith/bottomnavigationviewbehavior/BottomNavigationViewBehavior.java
 */
package com.nextcloud.talk.utils.animations;

import android.view.View;
import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.ViewCompat;

public class ViewHidingBehaviourAnimation extends CoordinatorLayout.Behavior<View> {

    private int height;
    private boolean slidingDown = false;

    @Override
    public boolean onLayoutChild(CoordinatorLayout parent, View child, int layoutDirection) {
        height = child.getHeight();
        return super.onLayoutChild(parent, child, layoutDirection);
    }

    @Override
    public boolean onStartNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View directTargetChild, @NonNull View
            target, int nestedScrollAxes) {
        return nestedScrollAxes == ViewCompat.SCROLL_AXIS_VERTICAL;
    }

    @Override
    public void onNestedScroll(@NonNull CoordinatorLayout coordinatorLayout, @NonNull View child, @NonNull View target, int dxConsumed, int
            dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if (dyConsumed > 0) {
            slideDown(child);
        } else if (dyConsumed < 0) {
            slideUp(child);
        }
    }

    private void slideUp(View child) {
        if (slidingDown) {
            slidingDown = false;
            child.clearAnimation();
            child.animate().translationY(0).setDuration(200);
        }
    }

    private void slideDown(View child) {
        if (!slidingDown) {
            slidingDown = true;
            child.clearAnimation();
            child.animate().translationY(height).setDuration(200);
        }
    }

}
