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
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import autodagger.AutoInjector;
import butterknife.BindView;
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
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

import javax.inject.Inject;
import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;

@AutoInjector(NextcloudTalkApplication.class)
public class SwitchAccountController extends BaseController {

    @Inject
    UserUtils userUtils;

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @Inject
    CookieManager cookieManager;

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
                reauthorizeFromImport(account);
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
                        null, Boolean.TRUE, null, userEntity.getId(), null, null, null)
                        .subscribe(new Observer<UserEntity>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(UserEntity userEntity) {
                                cookieManager.getCookieStore().removeAll();

                                userUtils.disableAllUsersWithoutId(userEntity.getId());
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(() -> getRouter().popCurrentController());
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

        if (args.containsKey(BundleKeys.INSTANCE.getKEY_IS_ACCOUNT_IMPORT())) {
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
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        swipeRefreshLayout.setEnabled(false);

        if (getActionBar() != null) {
            getActionBar().show();
        }

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(userItems, getActivity(), false);

            UserEntity userEntity;
            Participant participant;

            if (!isAccountImport) {
                for (Object userEntityObject : userUtils.getUsers()) {
                    userEntity = (UserEntity) userEntityObject;
                    if (!userEntity.getCurrent()) {
                        String userId;
                        if (userEntity.getUserId() != null) {
                            userId = userEntity.getUserId();
                        } else {
                            userId = userEntity.getUsername();
                        }

                        participant = new Participant();
                        participant.setActorType(Participant.ActorType.USERS);
                        participant.setActorId(userId);
                        participant.setDisplayName(userEntity.getDisplayName());
                        userItems.add(new AdvancedUserItem(participant, userEntity, null));
                    }
                }

                adapter.addListener(onSwitchItemClickListener);
                adapter.updateDataSet(userItems, false);
            } else {
                Account account;
                ImportAccount importAccount;
                for (Object accountObject : AccountUtils.INSTANCE.findAccounts(userUtils.getUsers())) {
                    account = (Account) accountObject;
                    importAccount = AccountUtils.INSTANCE.getInformationFromAccount(account);

                    participant = new Participant();
                    participant.setActorType(Participant.ActorType.USERS);
                    participant.setActorId(importAccount.getUsername());
                    participant.setDisplayName(importAccount.getUsername());
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


    private void prepareViews() {
        LinearLayoutManager layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setEnabled(false);
    }

    private void reauthorizeFromImport(Account account) {
        ImportAccount importAccount = AccountUtils.INSTANCE.getInformationFromAccount(account);
        Bundle bundle = new Bundle();
        bundle.putString(BundleKeys.INSTANCE.getKEY_BASE_URL(), importAccount.getBaseUrl());
        bundle.putString(BundleKeys.INSTANCE.getKEY_USERNAME(), importAccount.getUsername());
        bundle.putString(BundleKeys.INSTANCE.getKEY_TOKEN(), importAccount.getToken());
        bundle.putBoolean(BundleKeys.INSTANCE.getKEY_IS_ACCOUNT_IMPORT(), true);
        getRouter().pushController(RouterTransaction.with(new AccountVerificationController(bundle))
                .pushChangeHandler(new HorizontalChangeHandler())
                .popChangeHandler(new HorizontalChangeHandler()));
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_select_an_account);
    }
}
