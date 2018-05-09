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
 * Parts related to account import were either copied from or inspired by the great work done by David Luhmer at:
 * https://github.com/nextcloud/ownCloud-Account-Importer
 */

package com.nextcloud.talk.controllers;

import android.accounts.Account;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.items.AdvancedUserItem;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.models.ImportAccount;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.AccountUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
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
    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private List<AbstractFlexibleItem> userItems = new ArrayList<>();

    private boolean isAccountImport = false;

    private FlexibleAdapter.OnItemClickListener onImportItemClickListener = new FlexibleAdapter.OnItemClickListener() {
        @Override
        public boolean onItemClick(View view, int position) {
            if (userItems.size() > position) {
                Account account = ((AdvancedUserItem) userItems.get(position)).getAccount();
                verifyAccount(account);
            }

            return true;
        }
    };

    private FlexibleAdapter.OnItemClickListener onSwitchItemClickListener = new FlexibleAdapter.OnItemClickListener() {
        @Override
        public boolean onItemClick(View view, int position) {
            if (userItems.size() > position) {
                UserEntity userEntity = ((AdvancedUserItem) userItems.get(position)).getEntity();
                userUtils.createOrUpdateUser(null,
                        null, null, null,
                        null, true, null, userEntity.getId(), null, null)
                        .subscribe(new Observer<UserEntity>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(UserEntity userEntity) {
                                cookieManager.getCookieStore().removeAll();
                                userUtils.disableAllUsersWithoutId(userEntity.getId());
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            getRouter().popCurrentController();
                                        }
                                    });
                                }
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

    public SwitchAccountController() {
        setHasOptionsMenu(true);
    }

    public SwitchAccountController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);

        if (args.containsKey(BundleKeys.KEY_IS_ACCOUNT_IMPORT)) {
            isAccountImport = true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getRouter().popCurrentController();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

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

            if (!isAccountImport) {
                for (Object userEntityObject : userUtils.getUsers()) {
                    userEntity = (UserEntity) userEntityObject;
                    if (!userEntity.getCurrent()) {
                        participant = new Participant();
                        participant.setName(userEntity.getDisplayName());

                        String userId;

                        if (userEntity.getUserId() != null) {
                            userId = userEntity.getUserId();
                        } else {
                            userId = userEntity.getUsername();
                        }
                        participant.setUserId(userId);
                        userItems.add(new AdvancedUserItem(participant, userEntity, null));
                    }
                }

                adapter.addListener(onSwitchItemClickListener);
                adapter.updateDataSet(userItems, false);
            } else {
                if (getActionBar() != null) {
                    getActionBar().show();
                }
                Account account;
                ImportAccount importAccount;
                for (Object accountObject : AccountUtils.findAccounts(userUtils.getUsers())) {
                    account = (Account) accountObject;
                    importAccount = AccountUtils.getInformationFromAccount(account);

                    participant = new Participant();
                    participant.setName(importAccount.getUsername());
                    participant.setUserId(importAccount.getUsername());
                    userEntity = new UserEntity();
                    userEntity.setBaseUrl(importAccount.getBaseUrl());
                    userItems.add(new AdvancedUserItem(participant, userEntity, account));
                }

                adapter.addListener(onImportItemClickListener);
                adapter.updateDataSet(userItems, false);
            }

        }

        prepareViews();
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
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

    private void verifyAccount(Account account) {
        ImportAccount importAccount = AccountUtils.getInformationFromAccount(account);
        Bundle bundle = new Bundle();
        bundle.putString(BundleKeys.KEY_USERNAME, importAccount.getUsername());
        bundle.putString(BundleKeys.KEY_TOKEN, importAccount.getToken());
        bundle.putString(BundleKeys.KEY_BASE_URL, importAccount.getBaseUrl());
        bundle.putBoolean(BundleKeys.KEY_IS_ACCOUNT_IMPORT, true);
        getRouter().pushController(RouterTransaction.with(new AccountVerificationController
                (bundle)).pushChangeHandler(new HorizontalChangeHandler())
                .popChangeHandler(new HorizontalChangeHandler()));
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_select_an_account);
    }
}
