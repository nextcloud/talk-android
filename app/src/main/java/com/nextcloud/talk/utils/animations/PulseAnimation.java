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
 *
 * Original code taken from https://github.com/thunderrise/android-TNRAnimationHelper under MIT licence
 * and modified by yours truly to fit the needs of this awesome app.
 */

package com.nextcloud.talk.utils.animations;

import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.View;
import androidx.annotation.NonNull;

public class PulseAnimation {

    public static final int RESTART = 1;
    public static final int REVERSE = 2;
    public static final int INFINITE = -1;
    private ObjectAnimator scaleDown;
    private int duration = 310;
    private View view;
    private int repeatMode = ValueAnimator.RESTART;
    private int repeatCount = INFINITE;

    public static PulseAnimation create() {
        return new PulseAnimation();
    }

    public PulseAnimation with(@NonNull View view) {
        this.view = view;
        return this;
    }

    public void start() {

        if (view == null) throw new NullPointerException("View cant be null!");

        scaleDown = ObjectAnimator.ofPropertyValuesHolder(view, PropertyValuesHolder.ofFloat("scaleX", 1.2f), PropertyValuesHolder.ofFloat("scaleY", 1.2f));
        scaleDown.setDuration(duration);
        scaleDown.setRepeatMode(repeatMode);
        scaleDown.setRepeatCount(repeatCount);
        scaleDown.setAutoCancel(true);
        scaleDown.start();
    }

    public void stop() {
        if (scaleDown != null && view != null) {
            scaleDown.end();
            scaleDown.cancel();
            view.setScaleX(1.0f);
            view.setScaleY(1.0f);
        }
    }

    public PulseAnimation setDuration(int duration) {
        this.duration = duration;
        return this;
    }

    public PulseAnimation setRepeatMode(int repeatMode) {
        this.repeatMode = repeatMode;
        return this;
    }

    public PulseAnimation setRepeatCount(int repeatCount) {
        this.repeatCount = repeatCount;
        return this;
    }
}
