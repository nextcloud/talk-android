/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
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

import android.app.SearchManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.EditorInfo;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.items.RoomItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.api.helpers.api.ApiHelper;
import com.nextcloud.talk.api.models.json.rooms.Room;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.persistence.entities.UserEntity;
import com.nextcloud.talk.utils.database.cache.CacheUtils;
import com.nextcloud.talk.utils.database.user.UserUtils;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.FlexibleItemDecoration;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class CallsListController extends BaseController implements SearchView.OnQueryTextListener {

    public static final String TAG = "CallsListController";

    private static final String KEY_FROM_RESTORE_CONTROLLER = "CallsListController.fromRestoreController";
    private static final String KEY_FROM_RESTORE_VIEW = "CallsListController.fromRestoreView";
    private static final String KEY_SEARCH_QUERY = "ContactsController.searchQuery";

    @Inject
    UserUtils userUtils;

    @Inject
    CacheUtils cacheUtils;

    @Inject
    NcApi ncApi;
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;

    private UserEntity userEntity;
    private Disposable roomsQueryDisposable;
    private Disposable cacheQueryDisposable;
    private FlexibleAdapter<RoomItem> adapter;
    private List<RoomItem> roomItems = new ArrayList<>();

    private boolean isFromRestoreView;
    private boolean isFromRestoreController;

    private MenuItem searchItem;
    private SearchView searchView;
    private String searchQuery;

    public CallsListController() {
        super();
        setHasOptionsMenu(true);
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_generic_rv, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(roomItems, getActivity(), false);
        }

        prepareViews();

        if ((userEntity = userUtils.getCurrentUser()) != null) {
            if (!adapter.hasSearchText()) {
                if (!cacheUtils.cacheExistsForContext(TAG) || !isFromRestoreView) {
                    fetchData(true);
                } else if (cacheUtils.cacheExistsForContext(TAG) && isFromRestoreController) {
                    fetchData(false);
                }
            }
        } else {
            // Fallback to login if we have no users
            if (getParentController() != null && getParentController().getRouter() != null) {
                getParentController().getRouter().setRoot((RouterTransaction.with(new ServerSelectionController())
                        .pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler())));
            }
        }
    }

    private void initSearchView() {
        if (getActivity() != null) {
            SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
            if (searchItem != null) {
                searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
                searchView.setMaxWidth(Integer.MAX_VALUE);
                searchView.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER);
                searchView.setImeOptions(EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN);
                searchView.setQueryHint(getResources().getString(R.string.nc_search));
                if (searchManager != null) {
                    searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
                }
                searchView.setOnQueryTextListener(this);
            }
        }

        final View mSearchEditFrame = searchView
                .findViewById(android.support.v7.appcompat.R.id.search_edit_frame);

        BottomNavigationView bottomNavigationView = getParentController().getView().findViewById(R.id.navigation);

        Handler handler = new Handler();
        ViewTreeObserver vto = mSearchEditFrame.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            int oldVisibility = -1;

            @Override
            public void onGlobalLayout() {

                int currentVisibility = mSearchEditFrame.getVisibility();

                if (currentVisibility != oldVisibility) {
                    if (currentVisibility == View.VISIBLE) {
                        handler.postDelayed(() -> bottomNavigationView.setVisibility(View.GONE), 100);
                    } else {
                        handler.postDelayed(() -> {
                            bottomNavigationView.setVisibility(View.VISIBLE);
                            searchItem.setVisible(roomItems.size() > 0);
                        }, 500);
                    }

                    oldVisibility = currentVisibility;
                }

            }
        });

    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_filter, menu);
        searchItem = menu.findItem(R.id.action_search);
        initSearchView();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        searchItem.setVisible(roomItems.size() > 0);
        if (adapter.hasSearchText()) {
            searchItem.expandActionView();
            searchView.setQuery(adapter.getSearchText(), false);
        }
    }

    private void fetchData(boolean forceNew) {
        dispose(null);

        roomItems = new ArrayList<>();

        if (forceNew) {
            roomsQueryDisposable = ncApi.getRooms(ApiHelper.getCredentials(userEntity.getUsername(),
                    userEntity.getToken()), ApiHelper.getUrlForGetRooms(userEntity.getBaseUrl()))
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(roomsOverall -> {

                                if (roomsOverall != null) {
                                    for (int i = 0; i < roomsOverall.getOcs().getData().size(); i++) {
                                        roomItems.add(new RoomItem(roomsOverall.getOcs().getData().get(i), userEntity));
                                    }

                                    adapter.updateDataSet(roomItems, true);
                                    if (searchItem != null) {
                                        searchItem.setVisible(roomItems.size() > 0);
                                    }

                                    cacheQueryDisposable = cacheUtils.createOrUpdateViewCache(
                                            LoganSquare.serialize(roomsOverall.getOcs().getData()),
                                            userEntity.getId(), TAG).subscribe(cacheEntity -> {
                                                // do nothing
                                            }, throwable -> dispose(cacheQueryDisposable),
                                            () -> dispose(cacheQueryDisposable));
                                }
                            }, throwable -> {
                                if (searchItem != null) {
                                    searchItem.setVisible(false);
                                }
                                dispose(roomsQueryDisposable);
                            }
                            , () -> {
                                dispose(roomsQueryDisposable);
                                swipeRefreshLayout.setRefreshing(false);
                            });
        } else {
            cacheQueryDisposable = cacheUtils.getViewCache(userEntity.getId(), TAG)
                    .subscribe(o -> {
                                if (o != null) {
                                    List<Room> rooms = LoganSquare.parseList(o.getValue(), Room.class);
                                    for (Room room : rooms) {
                                        roomItems.add(new RoomItem(room, userEntity));
                                    }

                                    adapter.updateDataSet(roomItems, true);
                                    searchItem.setVisible(roomItems.size() > 0);
                                }
                            }, throwable -> {
                                if (searchItem != null) {
                                    searchItem.setVisible(false);
                                }
                                dispose(cacheQueryDisposable);
                            },
                            () -> dispose(cacheQueryDisposable));
        }
    }

    private void prepareViews() {
        LinearLayoutManager layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        recyclerView.setAdapter(adapter);

        RecyclerView.ItemDecoration dividerItemDecoration = new DividerItemDecoration(
                recyclerView.getContext(),
                layoutManager.getOrientation()
        );
        recyclerView.addItemDecoration(dividerItemDecoration);

        swipeRefreshLayout.setOnRefreshListener(() -> fetchData(true));
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);
    }

    private void dispose(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        } else if (disposable == null) {

            if (roomsQueryDisposable != null && !roomsQueryDisposable.isDisposed()) {
                roomsQueryDisposable.dispose();
                roomsQueryDisposable = null;
            }

            if (cacheQueryDisposable != null && !cacheQueryDisposable.isDisposed()) {
                cacheQueryDisposable.dispose();
                cacheQueryDisposable = null;
            }
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_FROM_RESTORE_CONTROLLER, true);
    }

    @Override
    public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        isFromRestoreController = savedInstanceState.getBoolean(KEY_FROM_RESTORE_CONTROLLER, false);
    }

    @Override
    public void onSaveViewState(@NonNull View view, @NonNull Bundle outState) {
        super.onSaveViewState(view, outState);
        outState.putBoolean(KEY_FROM_RESTORE_VIEW, true);
        if (searchView != null && !TextUtils.isEmpty(searchView.getQuery())) {
            outState.putString(KEY_SEARCH_QUERY, searchView.getQuery().toString());
        }
    }

    @Override
    public void onRestoreViewState(@NonNull View view, @NonNull Bundle savedViewState) {
        super.onRestoreViewState(view, savedViewState);
        isFromRestoreView = savedViewState.getBoolean(KEY_FROM_RESTORE_VIEW, false);
        searchQuery = savedViewState.getString(KEY_SEARCH_QUERY, "");
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (adapter.hasNewSearchText(newText) || !TextUtils.isEmpty(searchQuery)) {

            if (!TextUtils.isEmpty(searchQuery)) {
                adapter.setSearchText(searchQuery);
                searchQuery = "";
                adapter.filterItems();
            } else {
                adapter.setSearchText(newText);
                adapter.filterItems(300);
            }
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(!adapter.hasSearchText());
        }

        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return onQueryTextChange(query);
    }
}
