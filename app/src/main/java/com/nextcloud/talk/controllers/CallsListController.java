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
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomNavigationView;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DividerItemDecoration;
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
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.bluelinelabs.conductor.internal.NoOpControllerChangeHandler;
import com.kennyc.bottomsheet.BottomSheet;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.adapters.items.CallItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.controllers.bottomsheet.CallMenuController;
import com.nextcloud.talk.controllers.bottomsheet.EntryMenuController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.events.MoreMenuClickEvent;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.rooms.Room;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.KeyboardUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.fastscroller.FastScroller;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;

@AutoInjector(NextcloudTalkApplication.class)
public class CallsListController extends BaseController implements SearchView.OnQueryTextListener,
        FlexibleAdapter.OnItemClickListener, FastScroller.OnScrollStateChangeListener {

    public static final String TAG = "CallsListController";

    private static final String KEY_SEARCH_QUERY = "ContactsController.searchQuery";

    @Inject
    UserUtils userUtils;

    @Inject
    EventBus eventBus;

    @Inject
    NcApi ncApi;
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;

    @BindView(R.id.fast_scroller)
    FastScroller fastScroller;

    private UserEntity currentUser;
    private Disposable roomsQueryDisposable;
    private FlexibleAdapter<CallItem> adapter;
    private List<CallItem> callItems = new ArrayList<>();

    private BottomSheet bottomSheet;
    private MenuItem searchItem;
    private SearchView searchView;
    private String searchQuery;

    private View view;

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

        if (getActionBar() != null) {
            getActionBar().show();
        }

        currentUser = userUtils.getCurrentUser();

        if (currentUser == null &&
                getParentController() != null && getParentController().getRouter() != null) {
            getParentController().getRouter().setRoot((RouterTransaction.with(new ServerSelectionController())
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler())));
        }

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(callItems, getActivity(), false);
            if (currentUser != null) {
                fetchData(false);
            }
        }

        adapter.addListener(this);
        prepareViews();
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        eventBus.register(this);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(false);
        }

        currentUser = userUtils.getCurrentUser();
    }

    @Override
    protected void onDetach(@NonNull View view) {
        super.onDetach(view);
        eventBus.unregister(this);
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
                            searchItem.setVisible(callItems.size() > 0);
                        }, 500);
                    }

                    oldVisibility = currentVisibility;
                }

            }
        });

    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_new_conversation:
                Bundle bundle = new Bundle();
                bundle.putParcelable(BundleKeys.KEY_MENU_TYPE, Parcels.wrap(CallMenuController.MenuType.NEW_CONVERSATION));
                prepareAndShowBottomSheetWithBundle(bundle, true);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.menu_conversation_plus_filter, menu);
        searchItem = menu.findItem(R.id.action_search);
        initSearchView();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        searchItem.setVisible(callItems.size() > 0);
        if (adapter.hasFilter()) {
            searchItem.expandActionView();
            searchView.setQuery(adapter.getFilter(String.class), false);
        }
    }

    private void fetchData(boolean fromBottomSheet) {
        dispose(null);

        callItems = new ArrayList<>();

        roomsQueryDisposable = ncApi.getRooms(ApiUtils.getCredentials(currentUser.getUsername(),
                currentUser.getToken()), ApiUtils.getUrlForGetRooms(currentUser.getBaseUrl()))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(roomsOverall -> {

                    if (roomsOverall != null) {
                        for (int i = 0; i < roomsOverall.getOcs().getData().size(); i++) {
                            callItems.add(new CallItem(roomsOverall.getOcs().getData().get(i), currentUser));
                        }

                        adapter.updateDataSet(callItems, true);

                        Collections.sort(callItems, (callItem, t1) ->
                                Long.compare(t1.getModel().getLastPing(), callItem.getModel().getLastPing()));

                        if (searchItem != null) {
                            searchItem.setVisible(callItems.size() > 0);
                        }
                    }

                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }

                }, throwable -> {
                    if (searchItem != null) {
                        searchItem.setVisible(false);
                    }

                    if (throwable instanceof HttpException) {
                        HttpException exception = (HttpException) throwable;
                        switch (exception.code()) {
                            case 401:
                                if (getParentController() != null &&
                                        getParentController().getRouter() != null) {
                                    getParentController().getRouter().pushController((RouterTransaction.with
                                            (new WebViewLoginController(currentUser.getBaseUrl(),
                                                    true))
                                            .pushChangeHandler(new VerticalChangeHandler())
                                            .popChangeHandler(new VerticalChangeHandler())));
                                }
                                break;
                            default:
                                break;
                        }
                    }
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    dispose(roomsQueryDisposable);
                }, () -> {
                    dispose(roomsQueryDisposable);
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }

                    if (fromBottomSheet) {
                        new Handler().postDelayed(() -> {
                            bottomSheet.setCancelable(true);
                            if (bottomSheet.isShowing()) {
                                bottomSheet.cancel();
                            }
                        }, 2500);
                    }

                });

    }

    private void prepareViews() {
        SmoothScrollLinearLayoutManager layoutManager =
                new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new DividerItemDecoration(
                recyclerView.getContext(),
                layoutManager.getOrientation()
        ));

        swipeRefreshLayout.setOnRefreshListener(() -> fetchData(false));
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);

        fastScroller.addOnScrollStateChangeListener(this);
        adapter.setFastScroller(fastScroller);
        fastScroller.setBubbleTextCreator(position -> {
            String displayName = adapter.getItem(position).getModel().getDisplayName();
            if (displayName.length() > 8) {
                displayName = displayName.substring(0, 4) + "...";
            }
            return displayName;
        });
    }

    private void dispose(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            disposable = null;
        } else if (disposable == null &&
                roomsQueryDisposable != null && !roomsQueryDisposable.isDisposed()) {
            roomsQueryDisposable.dispose();
            roomsQueryDisposable = null;

        }
    }

    @Override
    public void onSaveViewState(@NonNull View view, @NonNull Bundle outState) {
        super.onSaveViewState(view, outState);
        if (searchView != null && !TextUtils.isEmpty(searchView.getQuery())) {
            outState.putString(KEY_SEARCH_QUERY, searchView.getQuery().toString());
        }
    }

    @Override
    public void onRestoreViewState(@NonNull View view, @NonNull Bundle savedViewState) {
        super.onRestoreViewState(view, savedViewState);
        searchQuery = savedViewState.getString(KEY_SEARCH_QUERY, "");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dispose(null);
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        if (adapter.hasNewFilter(newText) || !TextUtils.isEmpty(searchQuery)) {

            if (!TextUtils.isEmpty(searchQuery)) {
                adapter.setFilter(searchQuery);
                searchQuery = "";
                adapter.filterItems();
            } else {
                adapter.setFilter(newText);
                adapter.filterItems(300);
            }
        }

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setEnabled(!adapter.hasFilter());
        }

        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return onQueryTextChange(query);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(BottomSheetLockEvent bottomSheetLockEvent) {
        if (bottomSheet != null) {
            if (!bottomSheetLockEvent.isCancelable()) {
                bottomSheet.setCancelable(bottomSheetLockEvent.isCancelable());
            } else {
                if (bottomSheetLockEvent.getDelay() != 0 && bottomSheetLockEvent.isShouldRefreshData()) {
                    fetchData(true);
                } else {
                    bottomSheet.setCancelable(bottomSheetLockEvent.isCancelable());
                    if (bottomSheet.isShowing() && bottomSheetLockEvent.isCancel()) {
                        bottomSheet.cancel();
                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MoreMenuClickEvent moreMenuClickEvent) {
        Bundle bundle = new Bundle();
        Room room = moreMenuClickEvent.getRoom();
        bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(room));
        bundle.putParcelable(BundleKeys.KEY_MENU_TYPE, Parcels.wrap(CallMenuController.MenuType.REGULAR));

        prepareAndShowBottomSheetWithBundle(bundle, true);
    }

    private void prepareAndShowBottomSheetWithBundle(Bundle bundle, boolean shouldShowCallMenuController) {
        if (view == null) {
            view = getActivity().getLayoutInflater().inflate(R.layout.bottom_sheet, null, false);
        }

        if (shouldShowCallMenuController) {
            getChildRouter((ViewGroup) view).setRoot(
                    RouterTransaction.with(new CallMenuController(bundle))
                            .popChangeHandler(new VerticalChangeHandler())
                            .pushChangeHandler(new VerticalChangeHandler()));
        } else {
            getChildRouter((ViewGroup) view).setRoot(
                    RouterTransaction.with(new EntryMenuController(bundle))
                            .popChangeHandler(new VerticalChangeHandler())
                            .pushChangeHandler(new VerticalChangeHandler()));
        }

        if (bottomSheet == null) {
            bottomSheet = new BottomSheet.Builder(getActivity()).setView(view).create();
        }

        bottomSheet.setOnShowListener(dialog -> new KeyboardUtils(getActivity(), bottomSheet.getLayout(), true));
        bottomSheet.show();
    }


    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_app_name);
    }

    @Override
    public void onFastScrollerStateChange(boolean scrolling) {
        swipeRefreshLayout.setEnabled(!scrolling);
    }

    @Override
    public boolean onItemClick(View view, int position) {
        CallItem callItem = adapter.getItem(position);
        if (callItem != null && getActivity() != null) {
            Room room = callItem.getModel();
            Bundle bundle = new Bundle();
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, callItem.getModel().getToken());
            bundle.putString(BundleKeys.KEY_ROOM_ID, callItem.getModel().getRoomId());

            if (room.hasPassword && (room.participantType.equals(Participant.ParticipantType.GUEST) ||
                    room.participantType.equals(Participant.ParticipantType.USER_FOLLOWING_LINK))) {
                bundle.putInt(BundleKeys.KEY_OPERATION_CODE, 99);
                prepareAndShowBottomSheetWithBundle(bundle, false);
            } else {
                currentUser = userUtils.getCurrentUser();

                if (currentUser.hasSpreedCapabilityWithName("chat-v2")) {
                    bundle.putString(BundleKeys.KEY_CONVERSATION_NAME, room.getDisplayName());
                    getParentController().getRouter().pushController((RouterTransaction.with(new ChatController(bundle))
                            .pushChangeHandler(new HorizontalChangeHandler())
                            .popChangeHandler(new HorizontalChangeHandler())));
                } else {
                    overridePushHandler(new NoOpControllerChangeHandler());
                    overridePopHandler(new NoOpControllerChangeHandler());
                    Intent callIntent = new Intent(getActivity(), MagicCallActivity.class);
                    callIntent.putExtras(bundle);
                    startActivity(callIntent);
                }
            }
        }

        return true;
    }
}
