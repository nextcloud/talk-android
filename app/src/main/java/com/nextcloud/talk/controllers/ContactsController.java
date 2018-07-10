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
import android.widget.RelativeLayout;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.kennyc.bottomsheet.BottomSheet;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.adapters.items.ProgressItem;
import com.nextcloud.talk.adapters.items.UserHeaderItem;
import com.nextcloud.talk.adapters.items.UserItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.controllers.bottomsheet.OperationsMenuController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.rooms.Room;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.models.json.sharees.Sharee;
import com.nextcloud.talk.models.json.sharees.ShareesOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import butterknife.Optional;
import eu.davidea.fastscroller.FastScroller;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.SelectableAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import eu.davidea.flipview.FlipView;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;
import retrofit2.Response;

@AutoInjector(NextcloudTalkApplication.class)
public class ContactsController extends BaseController implements SearchView.OnQueryTextListener,
        FlexibleAdapter.OnItemClickListener, FastScroller.OnScrollStateChangeListener, FlexibleAdapter.EndlessScrollListener {

    public static final String TAG = "ContactsController";

    private static final String KEY_SEARCH_QUERY = "ContactsController.searchQuery";
    @Nullable
    @BindView(R.id.initial_relative_layout)
    public RelativeLayout initialRelativeLayout;
    @Nullable
    @BindView(R.id.secondary_relative_layout)
    public RelativeLayout secondaryRelativeLayout;
    @Inject
    UserUtils userUtils;
    @Inject
    NcApi ncApi;
    @Inject
    EventBus eventBus;
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;

    @BindView(R.id.fast_scroller)
    FastScroller fastScroller;

    private UserEntity currentUser;
    private Disposable contactsQueryDisposable;
    private Disposable cacheQueryDisposable;
    private FlexibleAdapter adapter;
    private List<AbstractFlexibleItem> contactItems = new ArrayList<>();
    private BottomSheet bottomSheet;
    private View view;
    private int currentPage;
    private int currentSearchPage;

    private SmoothScrollLinearLayoutManager layoutManager;

    private MenuItem searchItem;
    private SearchView searchView;
    private String searchQuery;

    private boolean isNewConversationView;
    private boolean isPublicCall;

    private HashMap<String, UserHeaderItem> userHeaderItems = new HashMap<>();

    private boolean alreadyFetching = false;
    private boolean canFetchFurther = true;
    private boolean canFetchSearchFurther = true;

    private MenuItem doneMenuItem;

    public ContactsController() {
        super();
        setHasOptionsMenu(true);
    }

    public ContactsController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        if (args.containsKey(BundleKeys.KEY_NEW_CONVERSATION)) {
            isNewConversationView = true;
        }
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        if (isNewConversationView) {
            return inflater.inflate(R.layout.controller_contacts_rv, container, false);
        } else {
            return inflater.inflate(R.layout.controller_generic_rv, container, false);
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        eventBus.register(this);

        if (isNewConversationView) {
            toggleNewCallHeaderVisibility(!isPublicCall);

            checkAndHandleDoneMenuItem();

            if (getActionBar() != null) {
                getActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        currentUser = userUtils.getCurrentUser();
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        FlipView.resetLayoutAnimationDelay(true, 1000L);
        FlipView.stopLayoutAnimation();

        currentUser = userUtils.getCurrentUser();

        if (currentUser == null &&
                getParentController() != null && getParentController().getRouter() != null) {
            getParentController().getRouter().setRoot((RouterTransaction.with(new ServerSelectionController())
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler())));
        }

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(contactItems, getActivity(), false);

            if (currentUser != null) {
                fetchData(true);
            }
        }

        setupAdapter();
        prepareViews();
    }

    private void setupAdapter() {
        adapter.setNotifyChangeOfUnfilteredItems(true)
                .setMode(SelectableAdapter.Mode.MULTI);

        adapter.setEndlessScrollListener(this, new ProgressItem());

        adapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
                adapter.filterItems();
                adapter.onLoadMoreComplete(null);
            }
        });

        adapter.setStickyHeaderElevation(5)
                .setUnlinkAllItemsOnRemoveHeaders(true)
                .setDisplayHeadersAtStartUp(true)
                .setStickyHeaders(true);

        adapter.addListener(this);
    }

    private void selectionDone() {
        if (!isPublicCall && adapter.getSelectedPositions().size() == 1) {
            RetrofitBucket retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(currentUser.getBaseUrl(), "1",
                    ((UserItem) adapter.getItem(adapter.getSelectedPositions().get(0))).getModel().getUserId(), null);
            ncApi.createRoom(ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                    retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<RoomOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(RoomOverall roomOverall) {
                            Intent conversationIntent = new Intent(getActivity(), MagicCallActivity.class);
                            Bundle bundle = new Bundle();
                            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.getOcs().getData().getToken());
                            bundle.putString(BundleKeys.KEY_ROOM_ID, roomOverall.getOcs().getData().getRoomId());

                            if (currentUser.hasSpreedCapabilityWithName("chat-v2")) {
                                bundle.putString(BundleKeys.KEY_CONVERSATION_NAME,
                                        roomOverall.getOcs().getData().getDisplayName());
                                conversationIntent.putExtras(bundle);
                                getRouter().pushController((RouterTransaction.with(new ChatController(bundle))
                                        .pushChangeHandler(new HorizontalChangeHandler())
                                        .popChangeHandler(new HorizontalChangeHandler())));
                            } else {
                                conversationIntent.putExtras(bundle);
                                startActivity(conversationIntent);
                                new Handler().postDelayed(() -> getRouter().popCurrentController(), 100);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {
                        }
                    });
        } else {

            Bundle bundle = new Bundle();
            Room.RoomType roomType;
            if (isPublicCall) {
                roomType = Room.RoomType.ROOM_PUBLIC_CALL;
            } else {
                roomType = Room.RoomType.ROOM_GROUP_CALL;
            }

            bundle.putParcelable(BundleKeys.KEY_CONVERSATION_TYPE, Parcels.wrap(roomType));
            ArrayList<String> userIds = new ArrayList<>();
            Set<Integer> selectedPositions = adapter.getSelectedPositionsAsSet();
            for (int selectedPosition : selectedPositions) {
                if (adapter.getItem(selectedPosition) instanceof UserItem) {
                    UserItem userItem = (UserItem) adapter.getItem(selectedPosition);
                    userIds.add(userItem.getModel().getUserId());
                }
            }
            bundle.putStringArrayList(BundleKeys.KEY_INVITED_PARTICIPANTS, userIds);
            bundle.putInt(BundleKeys.KEY_OPERATION_CODE, 11);
            prepareAndShowBottomSheetWithBundle(bundle);
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

        BottomNavigationView bottomNavigationView = null;
        if (getParentController() != null && getParentController().getView() != null) {
            bottomNavigationView = getParentController().getView().findViewById(R.id.navigation);
        }

        Handler handler = new Handler();
        ViewTreeObserver vto = mSearchEditFrame.getViewTreeObserver();
        BottomNavigationView finalBottomNavigationView = bottomNavigationView;
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            int oldVisibility = -1;

            @Override
            public void onGlobalLayout() {

                int currentVisibility = mSearchEditFrame.getVisibility();

                if (currentVisibility != oldVisibility) {
                    if (currentVisibility == View.VISIBLE) {
                        if (finalBottomNavigationView != null) {
                            handler.postDelayed(() -> finalBottomNavigationView.setVisibility(View.GONE), 100);
                        }
                    } else {
                        handler.postDelayed(() -> {
                            if (finalBottomNavigationView != null) {
                                finalBottomNavigationView.setVisibility(View.VISIBLE);
                            }
                            searchItem.setVisible(contactItems.size() > 0);
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
            case android.R.id.home:
                getRouter().popCurrentController();
                return true;
            case R.id.contacts_selection_done:
                selectionDone();
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
        doneMenuItem = menu.findItem(R.id.contacts_selection_done);
        menu.findItem(R.id.action_new_conversation).setVisible(false);

        initSearchView();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        searchItem.setVisible(contactItems.size() > 0);
        if (adapter.hasFilter()) {
            searchItem.expandActionView();
            searchView.setQuery((CharSequence) adapter.getFilter(String.class), false);
        }

    }

    private void fetchData(boolean startFromScratch) {
        dispose(null);

        alreadyFetching = true;
        Set<Sharee> shareeHashSet = new HashSet<>();

        userHeaderItems = new HashMap<>();


        String query = "";
        if (searchView != null && !TextUtils.isEmpty(searchView.getQuery())) {
            query = searchView.getQuery().toString();
        } else if (startFromScratch) {
            contactItems = new ArrayList<>();
        }

        RetrofitBucket retrofitBucket = ApiUtils.getRetrofitBucketForContactsSearch(currentUser.getBaseUrl(),
                query);

        int page = 1;
        if (!startFromScratch) {
            if (TextUtils.isEmpty(query)) {
                page = currentPage + 1;
            } else {
                page = currentSearchPage + 1;
            }
        }

        Map<String, Object> modifiedQueryMap = new HashMap<>(retrofitBucket.getQueryMap());
        modifiedQueryMap.put("page", page);
        modifiedQueryMap.put("perPage", 100);
        ncApi.getContactsWithSearchParam(
                ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                retrofitBucket.getUrl(), modifiedQueryMap)
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .subscribe(new Observer<Response>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        contactsQueryDisposable = d;
                    }

                    @Override
                    public void onNext(Response response) {
                        if (response.body() != null) {
                            ShareesOverall shareesOverall = (ShareesOverall) response.body();

                            if (shareesOverall.getOcs().getData().getUsers() != null) {
                                shareeHashSet.addAll(shareesOverall.getOcs().getData().getUsers());
                            }

                            if (shareesOverall.getOcs().getData().getExactUsers() != null &&
                                    shareesOverall.getOcs().getData().getExactUsers().getExactSharees() != null) {
                                shareeHashSet.addAll(shareesOverall.getOcs().getData().
                                        getExactUsers().getExactSharees());
                            }

                            if (TextUtils.isEmpty((CharSequence) modifiedQueryMap.get("search"))) {
                                canFetchFurther = !shareeHashSet.isEmpty();
                                currentPage = (int) modifiedQueryMap.get("page");
                            } else {
                                canFetchSearchFurther = !shareeHashSet.isEmpty();
                                currentSearchPage = (int) modifiedQueryMap.get("page");
                            }


                            Participant participant;

                            List<AbstractFlexibleItem> newUserItemList = new ArrayList<>();
                            newUserItemList.addAll(contactItems);
                            for (Sharee sharee : shareeHashSet) {
                                if (!sharee.getValue().getShareWith().equals(currentUser.getUsername())) {
                                    participant = new Participant();
                                    participant.setName(sharee.getLabel());
                                    String headerTitle;

                                    headerTitle = sharee.getLabel().substring(0, 1).toUpperCase();

                                    UserHeaderItem userHeaderItem;
                                    if (!userHeaderItems.containsKey(headerTitle)) {
                                        userHeaderItem = new UserHeaderItem(headerTitle);
                                        userHeaderItems.put(headerTitle, userHeaderItem);
                                    }

                                    participant.setUserId(sharee.getValue().getShareWith());

                                    UserItem newContactItem = new UserItem(participant, currentUser,
                                            userHeaderItems.get(headerTitle));

                                    if (!contactItems.contains(newContactItem)) {
                                        newUserItemList.add(newContactItem);
                                    }

                                }

                            }


                            boolean shouldFilterManually = false;
                            if (newUserItemList.size() == contactItems.size()) {
                                shouldFilterManually = true;
                            }

                            contactItems = newUserItemList;
                            userHeaderItems = new HashMap<>();

                            Collections.sort(newUserItemList, (o1, o2) -> {
                                String firstName;
                                String secondName;

                                if (o1 instanceof UserItem) {
                                    firstName = ((UserItem) o1).getModel().getName();
                                } else {
                                    firstName = ((UserHeaderItem) o1).getModel();
                                }

                                if (o2 instanceof UserItem) {
                                    secondName = ((UserItem) o2).getModel().getName();
                                } else {
                                    secondName = ((UserHeaderItem) o2).getModel();
                                }

                                return firstName.compareToIgnoreCase(secondName);
                            });


                            if (!shouldFilterManually) {
                                adapter.updateDataSet(newUserItemList, false);
                            } else {
                                adapter.filterItems();
                                adapter.onLoadMoreComplete(null);
                            }

                            searchItem.setVisible(newUserItemList.size() > 0);
                            if (swipeRefreshLayout != null) {
                                swipeRefreshLayout.setRefreshing(false);
                            }

                            if (isNewConversationView) {
                                checkAndHandleDoneMenuItem();
                            }
                        }
                        ;
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (searchItem != null) {
                            searchItem.setVisible(false);
                        }

                        if (e instanceof HttpException) {
                            HttpException exception = (HttpException) e;
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

                            if (swipeRefreshLayout != null) {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                        }

                        dispose(contactsQueryDisposable);

                    }

                    @Override
                    public void onComplete() {
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
                        }
                        dispose(contactsQueryDisposable);
                        alreadyFetching = false;

                    }
                });

    }

    private void prepareViews() {
        layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> fetchData(true));
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);

        fastScroller.addOnScrollStateChangeListener(this);
        adapter.setFastScroller(fastScroller);
        fastScroller.setBubbleTextCreator(position -> {
            IFlexible abstractFlexibleItem = adapter.getItem(position);
            if (abstractFlexibleItem instanceof UserItem) {
                return ((UserItem) adapter.getItem(position)).getHeader().getModel();
            } else if (abstractFlexibleItem instanceof UserHeaderItem) {
                return ((UserHeaderItem) adapter.getItem(position)).getModel();
            } else {
                return "";
            }
        });
    }

    private void dispose(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        } else if (disposable == null) {

            if (contactsQueryDisposable != null && !contactsQueryDisposable.isDisposed()) {
                contactsQueryDisposable.dispose();
                contactsQueryDisposable = null;
            }

            if (cacheQueryDisposable != null && !cacheQueryDisposable.isDisposed()) {
                cacheQueryDisposable.dispose();
                cacheQueryDisposable = null;
            }
        }
    }


    @Override
    public void onSaveViewState(@NonNull View view, @NonNull Bundle outState) {
        adapter.onSaveInstanceState(outState);
        super.onSaveViewState(view, outState);
        if (searchView != null && !TextUtils.isEmpty(searchView.getQuery())) {
            outState.putString(KEY_SEARCH_QUERY, searchView.getQuery().toString());
        }
    }

    @Override
    public void onRestoreViewState(@NonNull View view, @NonNull Bundle savedViewState) {
        super.onRestoreViewState(view, savedViewState);
        searchQuery = savedViewState.getString(KEY_SEARCH_QUERY, "");
        if (adapter != null) {
            adapter.onRestoreInstanceState(savedViewState);
        }
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
                adapter.filterItems();
                searchQuery = "";
            } else {
                adapter.setFilter(newText);
                if (TextUtils.isEmpty(newText)) {
                    adapter.filterItems();
                } else {
                    fetchData(true);
                }
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

    private void checkAndHandleDoneMenuItem() {
        if (adapter != null && doneMenuItem != null) {
            if (adapter.getSelectedItemCount() > 0 || isPublicCall) {
                if (!doneMenuItem.isVisible()) {
                    doneMenuItem.setVisible(true);
                }

            } else {
                doneMenuItem.setVisible(false);
            }
        } else if (doneMenuItem != null) {
            doneMenuItem.setVisible(false);
        }
    }

    @Override
    protected String getTitle() {
        if (!isNewConversationView) {
            return getResources().getString(R.string.nc_app_name);
        } else {
            return getResources().getString(R.string.nc_select_contacts);
        }
    }

    @Override
    public void onFastScrollerStateChange(boolean scrolling) {
        swipeRefreshLayout.setEnabled(!scrolling);
    }


    private void prepareAndShowBottomSheetWithBundle(Bundle bundle) {
        if (view == null) {
            view = getActivity().getLayoutInflater().inflate(R.layout.bottom_sheet, null, false);
        }

        getChildRouter((ViewGroup) view).setRoot(
                RouterTransaction.with(new OperationsMenuController(bundle))
                        .popChangeHandler(new VerticalChangeHandler())
                        .pushChangeHandler(new VerticalChangeHandler()));

        if (bottomSheet == null) {
            bottomSheet = new BottomSheet.Builder(getActivity()).setView(view).create();
        }

        bottomSheet.setOnCancelListener(dialog -> {
            if (getActionBar() != null) {
                getActionBar().setDisplayHomeAsUpEnabled(true);
            }
        });

        bottomSheet.setOnShowListener(dialog -> eventBus.post(new BottomSheetLockEvent(false, 0,
                false, false)));

        bottomSheet.show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(BottomSheetLockEvent bottomSheetLockEvent) {

        if (bottomSheet != null) {
            if (!bottomSheetLockEvent.isCancelable()) {
                bottomSheet.setCancelable(bottomSheetLockEvent.isCancelable());
            } else {
                bottomSheet.setCancelable(bottomSheetLockEvent.isCancelable());
                if (bottomSheet.isShowing() && bottomSheetLockEvent.isCancel()) {
                    new Handler().postDelayed(() -> {
                        bottomSheet.setOnCancelListener(null);
                        bottomSheet.cancel();

                    }, bottomSheetLockEvent.getDelay());
                }
            }
        }
    }

    @Override
    protected void onDetach(@NonNull View view) {
        super.onDetach(view);
        eventBus.unregister(this);
    }

    @Override
    public boolean onItemClick(View view, int position) {
        if (adapter.getItem(position) instanceof UserItem) {
            if (!isNewConversationView) {
                UserItem userItem = (UserItem) adapter.getItem(position);
                RetrofitBucket retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(currentUser.getBaseUrl(), "1",
                        userItem.getModel().getUserId(), null);
                ncApi.createRoom(ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken()),
                        retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<RoomOverall>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(RoomOverall roomOverall) {
                                if (getActivity() != null) {
                                    Intent conversationIntent = new Intent(getActivity(), MagicCallActivity.class);
                                    Bundle bundle = new Bundle();
                                    bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.getOcs().getData().getToken());
                                    bundle.putString(BundleKeys.KEY_ROOM_ID, roomOverall.getOcs().getData().getRoomId());
                                    conversationIntent.putExtras(bundle);

                                    if (currentUser.hasSpreedCapabilityWithName("chat-v2")) {
                                        bundle.putString(BundleKeys.KEY_CONVERSATION_NAME,
                                                roomOverall.getOcs().getData().getDisplayName());
                                        if (getParentController() != null) {
                                            getParentController().getRouter().pushController((RouterTransaction.with(new ChatController(bundle))
                                                    .pushChangeHandler(new HorizontalChangeHandler())
                                                    .popChangeHandler(new HorizontalChangeHandler())));
                                        }
                                    } else {
                                        startActivity(conversationIntent);
                                    }
                                }
                            }

                            @Override
                            public void onError(Throwable e) {

                            }

                            @Override
                            public void onComplete() {

                            }
                        });
            } else {
                ((UserItem) adapter.getItem(position)).flipItemSelection();
                adapter.toggleSelection(position);

                checkAndHandleDoneMenuItem();
            }
        }
        return true;
    }

    @Optional
    @OnClick(R.id.call_header_layout)
    void toggleCallHeader() {
        toggleNewCallHeaderVisibility(isPublicCall);
        isPublicCall = !isPublicCall;
        checkAndHandleDoneMenuItem();
    }

    private void toggleNewCallHeaderVisibility(boolean showInitialLayout) {
        if (showInitialLayout) {
            initialRelativeLayout.setVisibility(View.VISIBLE);
            secondaryRelativeLayout.setVisibility(View.GONE);
        } else {
            initialRelativeLayout.setVisibility(View.GONE);
            secondaryRelativeLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void noMoreLoad(int newItemsSize) {
    }

    @Override
    public void onLoadMore(int lastPosition, int currentPage) {
        String query = "";

        if (searchView != null && !TextUtils.isEmpty(searchView.getQuery())) {
            query = searchView.getQuery().toString();
        }

        if (!alreadyFetching && ((searchView != null && searchView.isIconified() && canFetchFurther)
                || (!TextUtils.isEmpty(query) && canFetchSearchFurther))) {
            fetchData(false);
        } else {
            adapter.onLoadMoreComplete(null);
        }
    }
}
