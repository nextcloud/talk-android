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

package com.nextcloud.talk.utils.animations;

import android.os.Bundle;
import android.transition.*;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.bluelinelabs.conductor.changehandler.SharedElementTransitionChangeHandler;

import java.util.ArrayList;
import java.util.List;

public class SharedElementTransition extends SharedElementTransitionChangeHandler {
    private static final String KEY_WAIT_FOR_TRANSITION_NAMES = "SharedElementTransition.names";

    private final ArrayList<String> names;

    public SharedElementTransition() {
        names = new ArrayList<>();
    }

    public SharedElementTransition(ArrayList<String> names) {
        this.names = names;
    }

    @Override
    public void saveToBundle(@NonNull Bundle bundle) {
        bundle.putStringArrayList(KEY_WAIT_FOR_TRANSITION_NAMES, names);
    }

    @Override
    public void restoreFromBundle(@NonNull Bundle bundle) {
        List<String> savedNames = bundle.getStringArrayList(KEY_WAIT_FOR_TRANSITION_NAMES);
        if (savedNames != null) {
            names.addAll(savedNames);
        }
    }

    @Nullable
    public Transition getExitTransition(@NonNull ViewGroup container, @Nullable View from, @Nullable View to, boolean isPush) {
        return new Fade(Fade.OUT);
    }

    @Override
    @Nullable
    public Transition getSharedElementTransition(@NonNull ViewGroup container, @Nullable View from, @Nullable View to, boolean isPush) {
        return new TransitionSet().addTransition(new ChangeBounds()).addTransition(new ChangeClipBounds()).addTransition(new ChangeTransform());
    }

    @Override
    @Nullable
    public Transition getEnterTransition(@NonNull ViewGroup container, @Nullable View from, @Nullable View to, boolean isPush) {
        return new Fade(Fade.IN);
    }

    @Override
    public void configureSharedElements(@NonNull ViewGroup container, @Nullable View from, @Nullable View to, boolean isPush) {
        for (String name : names) {
            addSharedElement(name);
            //waitOnSharedElementNamed(name);
        }
    }
}
