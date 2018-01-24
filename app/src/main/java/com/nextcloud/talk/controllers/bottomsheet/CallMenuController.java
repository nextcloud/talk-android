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

package com.nextcloud.talk.controllers.bottomsheet;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bluelinelabs.conductor.RouterTransaction;
import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.items.MenuItem;
import com.nextcloud.talk.api.models.json.rooms.Room;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.utils.bundle.BundleKeys;

import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;

@AutoInjector(NextcloudTalkApplication.class)
public class CallMenuController extends BaseController implements FlexibleAdapter.OnItemClickListener {
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @Inject
    EventBus eventBus;

    private Room room;
    private List<AbstractFlexibleItem> menuItems;
    private FlexibleAdapter<AbstractFlexibleItem> adapter;

    public CallMenuController(Bundle args) {
        super(args);
        this.room = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ROOM));
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_call_menu, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        prepareViews();
    }

    private void prepareViews() {
        LinearLayoutManager layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        prepareMenu();
        if (adapter == null) {
            adapter = new FlexibleAdapter<>(menuItems, getActivity(), false);
        }

        adapter.addListener(this);
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new DividerItemDecoration(
                recyclerView.getContext(),
                layoutManager.getOrientation()
        ));
    }

    private void prepareMenu() {
        menuItems = new ArrayList<>();

        menuItems.add(new MenuItem(getResources().getString(R.string.nc_what), 0));

        menuItems.add(new MenuItem(getResources().getString(R.string.nc_leave), 1));

        if (room.isNameEditable()) {
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_rename), 2));
        }

        if (room.canModerate()) {
            if (!room.isPublic()) {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_make_call_public), 3));
            } else {
                if (room.isHasPassword()) {
                    menuItems.add(new MenuItem(getResources().getString(R.string.nc_change_password), 4));
                } else {
                    menuItems.add(new MenuItem(getResources().getString(R.string.nc_set_password), 5));
                }
            }
        }

        if (room.isPublic()) {
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_share_link), 6));
            if (room.canModerate()) {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_make_call_private), 7));
            }
        }

        if (room.isDeletable()) {
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_delete_call), 8));
        }
    }

    @Override
    public boolean onItemClick(int position) {
        MenuItem menuItem = (MenuItem) adapter.getItem(position);
        Bundle bundle = new Bundle();
        bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(room));
        if (menuItem != null) {
            int tag = menuItem.getTag();
            if (tag > 0 && tag < 9) {
                eventBus.post(new BottomSheetLockEvent(false, 0, false));
                bundle.putInt(BundleKeys.KEY_OPERATION_CODE, tag);
                if (tag != 6 && tag != 2) {
                    getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle)));
                }
            }
        }

        return true;
    }
}
