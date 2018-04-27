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
 * The bottom navigation was taken from a PR to Conductor by Chris6647@gmail.com
 * https://github.com/bluelinelabs/Conductor/pull/316 and https://github.com/chris6647/Conductor/pull/1/files
 * and of course modified by yours truly.
 */

package com.nextcloud.talk.controllers;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.ColorRes;
import android.support.annotation.IdRes;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.Router;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.bottomnavigation.BottomNavigationController;
import com.nextcloud.talk.controllers.base.bottomnavigation.BottomNavigationMenuItem;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.animations.ViewHidingBehaviourAnimation;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.glide.GlideApp;

import java.lang.reflect.Constructor;

import javax.inject.Inject;

import autodagger.AutoInjector;

@AutoInjector(NextcloudTalkApplication.class)
public class MagicBottomNavigationController extends BottomNavigationController {
    @Inject
    UserUtils userUtils;

    private int settingsMenuItemIndex = 0;

    public MagicBottomNavigationController() {
        super(R.menu.menu_navigation);
    }

    /**
     * Supplied MenuItemId must match a {@link Controller} as defined in {@link
     * BottomNavigationMenuItem} or an {@link IllegalArgumentException} will be thrown.
     *
     * @param itemId
     */
    @Override
    protected Controller getControllerFor(@IdRes int itemId) {
        Constructor[] constructors =
                BottomNavigationMenuItem.getEnum(itemId).getControllerClass().getConstructors();
        Controller controller = null;
        try {
            /* Determine default or Bundle constructor */
            for (Constructor constructor : constructors) {
                if (constructor.getParameterTypes().length == 0) {
                    controller = (Controller) constructor.newInstance();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "An exception occurred while creating a new instance for mapping of "
                            + itemId
                            + ". "
                            + e.getMessage(),
                    e);
        }

        if (controller == null) {
            throw new RuntimeException(
                    "Controller must have a public empty constructor. "
                            + itemId);
        }

        return controller;
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        //getBottomNavigationView().setItemIconTintList(null);

        Menu menu = getMenu();
        int menuSize = menu.size();
        if (menuSize > 0) {
            settingsMenuItemIndex = menuSize - 1;
            MenuItem menuItem = menu.getItem(settingsMenuItemIndex);
            menuItem.getIcon().setTintList(null);
            // tint grey
            //menuItem.getIcon().clearColorFilter();
            //menuItem.getIcon().setColorFilter(context.getResources().getColor(R.color.grey600), PorterDuff.Mode.SRC_ATOP);
            //menuItem.setIcon(tintDrawable(menuItem.getIcon(), R.color.grey600));
            loadAvatarImage(menu.getItem(menuSize - 1));
        }
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
    }

    private void loadAvatarImage(MenuItem item) {
        UserEntity userEntity = userUtils.getCurrentUser();
        String avatarId;

        if (userEntity != null) {
            if (!TextUtils.isEmpty(userEntity.getUserId())) {
                avatarId = userEntity.getUserId();
            } else {
                avatarId = userEntity.getUsername();
            }

            GlideUrl glideUrl = new GlideUrl(ApiUtils.getUrlForAvatarWithName(userEntity.getBaseUrl(),
                    avatarId, true), new LazyHeaders.Builder()
                    .setHeader("Accept", "image/*")
                    .setHeader("User-Agent", ApiUtils.getUserAgent())
                    .build());

            Target target = new SimpleTarget<BitmapDrawable>(100, 100) {
                @Override
                public void onResourceReady(BitmapDrawable resource, Transition<? super BitmapDrawable> transition) {
                    item.setIcon(resource);
                }
            };

            GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                    .load(glideUrl)
                    .centerInside()
                    .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                    .circleCrop()
                    .into(target);
        }
    }

    private void tintBottomNavigation(int itemId) {
        int currentSelection = getCurrentlySelectedItemId();
        if (currentSelection != itemId) {

            /* Ensure correct tinting based on new selection */
            Menu menu = getMenu();
            Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
            for (int i = 0; i < menu.size(); i++) {
                if (i > settingsMenuItemIndex || i < settingsMenuItemIndex) {
                    MenuItem menuItem = menu.getItem(i);
                    if (menuItem.getItemId() != itemId) {
                        // tint grey
                        menuItem.getIcon().clearColorFilter();
                        menuItem.getIcon().setColorFilter(context.getResources().getColor(R.color.grey600), PorterDuff.Mode.SRC_ATOP);
                        //menuItem.setIcon(tintDrawable(menuItem.getIcon(), R.color.grey600));
                    } else if (menuItem.getItemId() == itemId) {
                        // tint primary
                        menuItem.getIcon().clearColorFilter();
                        menuItem.setIcon(tintDrawable(menuItem.getIcon(), R.color.colorPrimary));
                    }
                }
            }
        }
    }

    private static Drawable tintDrawable(Drawable drawable, @ColorRes int color) {
        if (drawable != null) {
            Drawable wrap = DrawableCompat.wrap(drawable);
            Context context = NextcloudTalkApplication.getSharedApplication().getApplicationContext();
            wrap.setColorFilter(context.getResources().getColor(color), PorterDuff.Mode.SRC_ATOP);
            return wrap;
        }

        return null;
    }
}
