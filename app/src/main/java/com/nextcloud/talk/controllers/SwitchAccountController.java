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

import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.items.UserItem;
import com.nextcloud.talk.api.models.json.participants.Participant;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.persistence.entities.UserEntity;
import com.nextcloud.talk.utils.database.user.UserUtils;

import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

@AutoInjector(NextcloudTalkApplication.class)
public class SwitchAccountController extends BaseController {

    @Inject
    UserUtils userUtils;

    @Inject
    CookieManager cookieManager;

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;
    private FlexibleAdapter<UserItem> adapter;
    private List<UserItem> userItems = new ArrayList<>();

    private FlexibleAdapter.OnItemClickListener onItemClickListener =
            new FlexibleAdapter.OnItemClickListener() {
                @Override
                public boolean onItemClick(int position) {
                    if (userItems.size() > position) {
                        UserEntity userEntity = userItems.get(position).getEntity();
                        userUtils.createOrUpdateUser(userEntity.getUsername(),
                                userEntity.getToken(), userEntity.getBaseUrl(), null,
                                null, true)
                                .subscribe(new Observer<UserEntity>() {
                                    @Override
                                    public void onSubscribe(Disposable d) {

                                    }

                                    @Override
                                    public void onNext(UserEntity userEntity) {
                                        cookieManager.getCookieStore().removeAll();
                                        userUtils.disableAllUsersWithoutId(userEntity.getId());
                                        getRouter().popCurrentController();
                                    }

                                    @Override
                                    public void onError(Throwable e) {

                                    }

                                    @Override
                                    public void onComplete() {

                                    }
                                });
                    }

                    return true;
                }
            };

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_generic_rv, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        swipeRefreshLayout.setEnabled(false);

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(userItems, getActivity(), false);

            UserEntity userEntity;
            Participant participant;

            for (Object userEntityObject : userUtils.getUsers()) {
                userEntity = (UserEntity) userEntityObject;
                if (!userEntity.getCurrent()) {
                    participant = new Participant();
                    participant.setName(userEntity.getDisplayName());
                    participant.setUserId(userEntity.getUsername());
                    userItems.add(new UserItem(participant, userEntity));
                }
            }

            adapter.addListener(onItemClickListener);
            adapter.updateDataSet(userItems, false);
        }

        prepareViews();
    }

    private void prepareViews() {
        LinearLayoutManager layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new DividerItemDecoration(
                recyclerView.getContext(),
                layoutManager.getOrientation()
        ));

        swipeRefreshLayout.setEnabled(false);
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_select_an_account);
    }


}
