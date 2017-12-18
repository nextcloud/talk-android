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

package com.nextcloud.talk.controllers;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.items.MenuItem;
import com.nextcloud.talk.api.models.json.rooms.Room;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;

import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;

import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;

@AutoInjector(NextcloudTalkApplication.class)
public class RoomMenuController extends BaseController implements FlexibleAdapter.OnItemClickListener {
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;
    private Room room;
    private List<AbstractFlexibleItem> menuItems;
    private FlexibleAdapter<AbstractFlexibleItem> adapter;

    public RoomMenuController(Bundle args) {
        super(args);
        this.room = Parcels.unwrap(args.getParcelable("room"));
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_room_menu, container, false);
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

        menuItems.add(new MenuItem(getResources().getString(R.string.nc_what)));

        menuItems.add(new MenuItem(getResources().getString(R.string.nc_leave)));

        if (room.isNameEditable()) {
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_rename)));
        }

        if (!room.isPublic()) {
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_make_call_public)));
        } else {
            if (room.isHasPassword()) {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_change_password)));
            } else {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_set_password)));
            }

            menuItems.add(new MenuItem(getResources().getString(R.string.nc_share_link)));
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_stop_sharing)));

        }

        if (room.isDeletable()) {
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_delete_call)));
        }
    }

    @Override
    public boolean onItemClick(int position) {
        if (menuItems.size() > position && position != 0) {
            MenuItem menuItem = (MenuItem) menuItems.get(position);
        }

        return true;
    }
}
