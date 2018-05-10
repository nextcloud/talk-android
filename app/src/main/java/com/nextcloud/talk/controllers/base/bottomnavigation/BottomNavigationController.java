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
 *
 * The bottom navigation was taken from a PR to Conductor by Chris6647@gmail.com
 * https://github.com/bluelinelabs/Conductor/pull/316 and https://github.com/chris6647/Conductor/pull/1/files
 * and of course modified by yours truly.
 */

package com.nextcloud.talk.controllers.base.bottomnavigation;

import android.os.Bundle;
import android.support.annotation.MenuRes;
import android.support.annotation.NonNull;
import android.support.design.widget.BottomNavigationView;
import android.support.design.widget.CoordinatorLayout;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.bluelinelabs.conductor.ChangeHandlerFrameLayout;
import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.Router;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.FadeChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.utils.animations.ViewHidingBehaviourAnimation;
import com.nextcloud.talk.utils.bundle.BundleBuilder;

import butterknife.BindView;

/**
 * The {@link Controller} for the Bottom Navigation View. Populates a {@link BottomNavigationView}
 * with the supplied {@link Menu} resource. The first item set as checked will be shown by default.
 * The backstack of each {@link MenuItem} is switched out, in order to maintain a separate backstack
 * for each {@link MenuItem} - even though that is against the Google Design Guidelines:
 *
 * @author chris6647@gmail.com
 * @see <a
 * href="https://material.io/guidelines/components/bottom-navigation.html#bottom-navigation-behavior">Material
 * Design Guidelines</a>
 *
 * Internally works similarly to {@link com.bluelinelabs.conductor.support.RouterPagerAdapter},
 * in the sense that it keeps track of the currently active {@link MenuItem} and the paired
 * Child {@link Router}. Everytime we navigate from one to another,
 * or {@link Controller#onSaveInstanceState(Bundle)} is called, we save the entire instance state
 * of the Child {@link Router}, and cache it, so we have it available when we navigate to
 * another {@link MenuItem} and can then restore the correct Child {@link Router}
 * (and thus the entire backstack)
 */
public abstract class BottomNavigationController extends BaseController {

    public static final String TAG = "BottomNavigationContr";
    public static final int INVALID_INT = -1;
    private static final String KEY_MENU_RESOURCE = "key_menu_resource";
    private static final String KEY_STATE_ROUTER_BUNDLES = "key_state_router_bundles";
    private static final String KEY_STATE_CURRENTLY_SELECTED_ID = "key_state_currently_selected_id";
    @BindView(R.id.navigation)
    BottomNavigationView bottomNavigationView;

    @BindView(R.id.bottom_navigation_controller_container)
    ChangeHandlerFrameLayout controllerContainer;

    private int currentlySelectedItemId = BottomNavigationController.INVALID_INT;

    private SparseArray<Bundle> routerSavedStateBundles;

    public BottomNavigationController(@MenuRes int menu) {
        this(new BundleBuilder(new Bundle()).putInt(KEY_MENU_RESOURCE, menu).build());
    }

    public BottomNavigationController(Bundle args) {
        super(args);
    }

    @NonNull
    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_bottom_navigation, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        /* Setup the BottomNavigationView with the constructor supplied Menu resource */
        bottomNavigationView.inflateMenu(getMenuResource());
        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int nextItemId = item.getItemId();
            if (currentlySelectedItemId != nextItemId) {
                Router oldChildRouter = getChildRouter(currentlySelectedItemId);
                save(oldChildRouter, currentlySelectedItemId);
                destroyChildRouter(oldChildRouter);

                configureRouter(getChildRouter(nextItemId), nextItemId);
                currentlySelectedItemId = nextItemId;
            } else {
                resetCurrentBackstack();
            }
            return true;
        });

        CoordinatorLayout.LayoutParams layoutParams = (CoordinatorLayout.LayoutParams) bottomNavigationView.getLayoutParams();
        layoutParams.setBehavior(new ViewHidingBehaviourAnimation());
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        /*
         * Fresh start, setup everything.
         * Must be done in onAttach to avoid artifacts if using multiple Activities,
         * and in case of resuming the app (i.e. when the view is not created again)
         */
        if (routerSavedStateBundles == null) {
            Menu menu = bottomNavigationView.getMenu();
            int menuSize = menu.size();
            routerSavedStateBundles = new SparseArray<>(menuSize);
            for (int i = 0; i < menuSize; i++) {
                MenuItem menuItem = menu.getItem(i);
                /* Ensure the first checked item is shown */
                if (menuItem.isChecked()) {
                    /*
                     * Seems like the BottomNavigationView always initializes index 0 as isChecked / Selected,
                     * regardless of what was set in the menu xml originally.
                     * So basically all we're doing here is always setting up menuItem index 0.
                     */
                    int itemId = menuItem.getItemId();
                    configureRouter(getChildRouter(itemId), itemId);
                    bottomNavigationView.setSelectedItemId(itemId);
                    currentlySelectedItemId = bottomNavigationView.getSelectedItemId();
                    break;
                }
            }
        } else {
            /*
             * Since we are restoring our state,
             * and onRestoreInstanceState is called before onViewBound,
             * all we need to do is rebind.
             */
            getChildRouter(currentlySelectedItemId).rebindIfNeeded();
        }
    }

    /**
     * Get the Child {@link Router} matching the supplied ItemId.
     *
     * @param itemId MenuItem ID
     * @return
     */
    protected Router getChildRouter(int itemId) {
        return getChildRouter(controllerContainer, "itemId:" + itemId);
    }

    /**
     * Correctly configure the {@link Router} given the cached routerSavedState.
     *
     * @param itemId {@link MenuItem} ID
     * @return true if {@link Router} was restored
     */
    private void configureRouter(@NonNull Router childRouter, int itemId) {
        if (!childRouter.hasRootController()) {
            Bundle routerSavedState = routerSavedStateBundles.get(itemId);
            if (routerSavedState != null && !routerSavedState.isEmpty()) {
                childRouter.restoreInstanceState(routerSavedState);
                routerSavedStateBundles.remove(itemId);
            } else {
                childRouter.setRoot(RouterTransaction.with(getControllerFor(itemId)));
            }
        }
        childRouter.rebindIfNeeded();
    }

    /**
     * Remove the supplied {@link Router} as a child of this Controller.
     *
     * @param childRouter
     */
    protected void destroyChildRouter(@NonNull Router childRouter) {
        removeChildRouter(childRouter);
    }

    /**
     * Resets the current backstack to the {@link Controller}, supplied by {@link
     * BottomNavigationController#getControllerFor(int)}, using a {@link FadeChangeHandler}.
     */
    protected void resetCurrentBackstack() {
        if (currentlySelectedItemId != BottomNavigationController.INVALID_INT) {
            destroyChildRouter(getChildRouter(currentlySelectedItemId));
            routerSavedStateBundles.remove(currentlySelectedItemId);
            /* Must get reference to newly recreated childRouter to avoid old view not being removed */
            getChildRouter(currentlySelectedItemId).setRoot(
                    RouterTransaction.with(getControllerFor(currentlySelectedItemId))
                            .pushChangeHandler(new FadeChangeHandler(true)));
        } else {
            Log.w(TAG,
                    "Attempted to reset backstack on BottomNavigationController with currentlySelectedItemId=" +
                            currentlySelectedItemId);
        }
    }

    /**
     * Navigate to the supplied {@link Controller}, while setting the menuItemId as selected on the
     * {@link BottomNavigationView}.
     *
     * @param itemId {@link MenuItem} ID
     */
    protected void navigateTo(int itemId) {
        if (currentlySelectedItemId != itemId) {
            destroyChildRouter(getChildRouter(currentlySelectedItemId));
            routerSavedStateBundles.remove(currentlySelectedItemId);

            /* Ensure correct Checked state based on new selection */
            Menu menu = bottomNavigationView.getMenu();
            for (int i = 0; i < menu.size(); i++) {
                MenuItem menuItem = menu.getItem(i);
                if (menuItem.isChecked() && menuItem.getItemId() != itemId) {
                    menuItem.setChecked(false);
                } else if (menuItem.getItemId() == itemId) {
                    menuItem.setChecked(true);
                }
            }

            configureRouter(getChildRouter(itemId), itemId);
            currentlySelectedItemId = itemId;
        } else {
            resetCurrentBackstack();
        }
    }

    /**
     * Saves the Child {@link Router} into a {@link Bundle} and caches that {@link Bundle}.
     * <p>
     * Be cautious as this call causes the controller flag it needs reattach,
     * so it should only be called just prior to destroying the router
     *
     * @param itemId {@link MenuItem} ID
     */
    private void save(Router childRouter, int itemId) {
        Bundle routerBundle = new Bundle();
        childRouter.saveInstanceState(routerBundle);
        routerSavedStateBundles.put(itemId, routerBundle);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        routerSavedStateBundles = savedInstanceState.getSparseParcelableArray(KEY_STATE_ROUTER_BUNDLES);
        currentlySelectedItemId =
                savedInstanceState.getInt(KEY_STATE_CURRENTLY_SELECTED_ID, BottomNavigationController.INVALID_INT);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putSparseParcelableArray(KEY_STATE_ROUTER_BUNDLES, routerSavedStateBundles);
        /*
         * For some reason the BottomNavigationView does not seem to correctly restore its
         * selectedId, even though the view appears with the correct state.
         * So we keep track of it manually
         */
        outState.putInt(KEY_STATE_CURRENTLY_SELECTED_ID, currentlySelectedItemId);
    }

    @Override
    public boolean handleBack() {
        /*
         * The childRouter should handleBack,
         * as this BottomNavigationController doesn't have a back step sensible to the user.
         */
        Router childRouter = getChildRouter(currentlySelectedItemId);
        if (childRouter != null) {
            if (childRouter.getBackstackSize() > 1) {
                return childRouter.handleBack();
            } else if (currentlySelectedItemId != R.id.navigation_calls) {
                navigateTo(R.id.navigation_calls);
                return true;
            } else {
                return false;
            }
        } else {
            Log.d(TAG, "handleBack called with getChildRouter(currentlySelectedItemId) == null.");
            return false;
        }
    }

    /**
     * Get the {@link Menu} Resource ID from {@link Controller#getArgs()}
     *
     * @return the {@link Menu} Resource ID
     */
    private int getMenuResource() {
        return getArgs().getInt(KEY_MENU_RESOURCE);
    }

    /**
     * Return a target instance of {@link Controller} for given menu item ID
     *
     * @param itemId the ID tapped by the user
     * @return the {@link Controller} instance to navigate to
     */
    protected abstract Controller getControllerFor(int itemId);
}