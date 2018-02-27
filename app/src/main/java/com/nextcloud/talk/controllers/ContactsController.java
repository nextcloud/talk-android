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
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.LinearLayout;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.bluelinelabs.conductor.internal.NoOpControllerChangeHandler;
import com.kennyc.bottomsheet.BottomSheet;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.CallActivity;
import com.nextcloud.talk.adapters.items.EmptyFooterItem;
import com.nextcloud.talk.adapters.items.NewCallHeaderItem;
import com.nextcloud.talk.adapters.items.UserHeaderItem;
import com.nextcloud.talk.adapters.items.UserItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.controllers.bottomsheet.EntryMenuController;
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

@AutoInjector(NextcloudTalkApplication.class)
public class ContactsController extends BaseController implements SearchView.OnQueryTextListener,
        FlexibleAdapter.OnItemClickListener, FastScroller.OnScrollStateChangeListener {

    public static final String TAG = "ContactsController";

    private static final String KEY_SEARCH_QUERY = "ContactsController.searchQuery";

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

    @Nullable
    @BindView(R.id.bottom_buttons_layout)
    LinearLayout bottomButtonsLinearLayout;

    @BindView(R.id.fast_scroller)
    FastScroller fastScroller;

    @Nullable
    @BindView(R.id.clear_button)
    Button clearButton;

    private UserEntity userEntity;
    private Disposable contactsQueryDisposable;
    private Disposable cacheQueryDisposable;
    private FlexibleAdapter adapter;
    private List<AbstractFlexibleItem> contactItems = new ArrayList<>();
    private BottomSheet bottomSheet;
    private View view;

    private SmoothScrollLinearLayoutManager layoutManager;

    private MenuItem searchItem;
    private SearchView searchView;
    private String searchQuery;

    private boolean isNewConversationView;
    private boolean isPublicCall;

    private HashMap<String, UserHeaderItem> userHeaderItems = new HashMap<String, UserHeaderItem>();

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
        return inflater.inflate(R.layout.controller_generic_rv, container, false);
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        eventBus.register(this);

        if (isNewConversationView) {
            checkAndHandleBottomButtons();

            if (getActionBar() != null) {
                getActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        if (isNewConversationView) {
            getParentController().getView().findViewById(R.id.navigation).setVisibility(View.GONE);
        }

        FlipView.resetLayoutAnimationDelay(true, 1000L);
        FlipView.stopLayoutAnimation();

        userEntity = userUtils.getCurrentUser();

        if (userEntity == null &&
                getParentController() != null && getParentController().getRouter() != null) {
            getParentController().getRouter().setRoot((RouterTransaction.with(new ServerSelectionController())
                    .pushChangeHandler(new HorizontalChangeHandler())
                    .popChangeHandler(new HorizontalChangeHandler())));
        }

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(contactItems, getActivity(), false);
            adapter.setNotifyChangeOfUnfilteredItems(true)
                    .setMode(SelectableAdapter.Mode.MULTI);

            if (userEntity != null) {
                fetchData();
            }
        }

        adapter.setStickyHeaderElevation(5)
                .setUnlinkAllItemsOnRemoveHeaders(true)
                .setDisplayHeadersAtStartUp(true)
                .setStickyHeaders(true);

        adapter.addListener(this);
        prepareViews();
    }

    @Optional
    @OnClick(R.id.clear_button)
    public void onClearButtonClick() {
        if (adapter != null) {
            List<Integer> selectedPositions = adapter.getSelectedPositions();
            for (Integer position : selectedPositions) {
                if (adapter.getItem(position) instanceof UserItem) {
                    UserItem userItem = (UserItem) adapter.getItem(position);
                    adapter.toggleSelection(position);
                    if (userItem != null) {
                        userItem.flipItemSelection();
                    }
                }
            }
        }

        checkAndHandleBottomButtons();
    }

    @Optional
    @OnClick(R.id.done_button)
    public void onDoneButtonClick() {

        if (!isPublicCall && adapter.getSelectedPositions().size() == 1) {
            RetrofitBucket retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(userEntity.getBaseUrl(), "1",
                    ((UserItem) adapter.getItem(adapter.getSelectedPositions().get(0))).getModel().getUserId(), null);
            ncApi.createRoom(ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken()),
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
                                overridePushHandler(new NoOpControllerChangeHandler());
                                overridePopHandler(new NoOpControllerChangeHandler());
                                Intent callIntent = new Intent(getActivity(), CallActivity.class);
                                Bundle bundle = new Bundle();
                                bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.getOcs().getData().getToken());
                                bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, Parcels.wrap(userEntity));
                                callIntent.putExtras(bundle);
                                startActivity(callIntent);
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
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_conversation_plus_filter, menu);
        searchItem = menu.findItem(R.id.action_search);
        menu.findItem(R.id.action_new_conversation).setVisible(false);
        initSearchView();
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        searchItem.setVisible(contactItems.size() > 0);
        if (adapter.hasSearchText()) {
            searchItem.expandActionView();
            searchView.setQuery(adapter.getSearchText(), false);
        }

    }

    private void fetchData() {
        dispose(null);

        Set<Sharee> shareeHashSet = new HashSet<>();

        contactItems = new ArrayList<>();
        userHeaderItems = new HashMap<>();

        RetrofitBucket retrofitBucket = ApiUtils.getRetrofitBucketForContactsSearch(userEntity.getBaseUrl(),
                "");
        contactsQueryDisposable = ncApi.getContactsWithSearchParam(
                ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken()),
                retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe((ShareesOverall shareesOverall) -> {
                            if (shareesOverall != null) {

                                if (shareesOverall.getOcs().getData().getUsers() != null) {
                                    shareeHashSet.addAll(shareesOverall.getOcs().getData().getUsers());
                                }

                                if (shareesOverall.getOcs().getData().getExactUsers() != null &&
                                        shareesOverall.getOcs().getData().getExactUsers().getExactSharees() != null) {
                                    shareeHashSet.addAll(shareesOverall.getOcs().getData().
                                            getExactUsers().getExactSharees());
                                }

                                Participant participant;
                                for (Sharee sharee : shareeHashSet) {
                                    if (!sharee.getValue().getShareWith().equals(userEntity.getUsername())) {
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
                                        contactItems.add(new UserItem(participant, userEntity,
                                                userHeaderItems.get(headerTitle)));
                                    }

                                }


                                userHeaderItems = new HashMap<>();

                                Collections.sort(contactItems, (o1, o2) -> {
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

                                if (isNewConversationView) {
                                    contactItems.add(0, new NewCallHeaderItem());
                                }

                                adapter.updateDataSet(contactItems, true);
                                searchItem.setVisible(contactItems.size() > 0);
                                swipeRefreshLayout.setRefreshing(false);


                                if (isNewConversationView) {
                                    checkAndHandleBottomButtons();
                                }
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
                                                    (new WebViewLoginController(userEntity.getBaseUrl(),
                                                            true))
                                                    .pushChangeHandler(new VerticalChangeHandler())
                                                    .popChangeHandler(new VerticalChangeHandler())));
                                        }
                                        break;
                                    default:
                                        break;
                                }
                            }

                            swipeRefreshLayout.setRefreshing(false);
                            dispose(contactsQueryDisposable);
                        }
                        , () -> {
                            swipeRefreshLayout.setRefreshing(false);
                            dispose(contactsQueryDisposable);
                        });
    }

    private void prepareViews() {
        layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        recyclerView.addItemDecoration(new DividerItemDecoration(
                recyclerView.getContext(),
                layoutManager.getOrientation()
        ));

        swipeRefreshLayout.setOnRefreshListener(this::fetchData);
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);

        fastScroller.addOnScrollStateChangeListener(this);
        adapter.setFastScroller(fastScroller);
        fastScroller.setBubbleTextCreator(position -> {
            IFlexible abstractFlexibleItem = adapter.getItem(position);
            if (abstractFlexibleItem instanceof UserItem) {
                return ((UserItem) adapter.getItem(position)).getHeader().getModel();
            } else {
                return ((UserHeaderItem) adapter.getItem(position)).getModel();
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

    @Override
    public boolean onItemClick(int position) {
        if (adapter.getItem(position) instanceof UserItem) {
            if (!isNewConversationView) {
                UserItem userItem = (UserItem) adapter.getItem(position);
                RetrofitBucket retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(userEntity.getBaseUrl(), "1",
                        userItem.getModel().getUserId(), null);
                ncApi.createRoom(ApiUtils.getCredentials(userEntity.getUsername(), userEntity.getToken()),
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
                                    overridePushHandler(new NoOpControllerChangeHandler());
                                    overridePopHandler(new NoOpControllerChangeHandler());
                                    Intent callIntent = new Intent(getActivity(), CallActivity.class);
                                    Bundle bundle = new Bundle();
                                    bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.getOcs().getData().getToken());
                                    bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, Parcels.wrap(userEntity));
                                    callIntent.putExtras(bundle);
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

                checkAndHandleBottomButtons();
            }
        } else if (adapter.getItem(position) instanceof NewCallHeaderItem) {
            adapter.toggleSelection(position);
            isPublicCall = adapter.isSelected(position);
            ((NewCallHeaderItem) adapter.getItem(position)).togglePublicCall(isPublicCall);
            checkAndHandleBottomButtons();
        }
        return true;
    }

    private void checkAndHandleBottomButtons() {
        if (adapter != null && bottomButtonsLinearLayout != null && clearButton != null) {
            if (adapter.getSelectedItemCount() > 0 || isPublicCall) {
                if (bottomButtonsLinearLayout.getVisibility() != View.VISIBLE) {
                    bottomButtonsLinearLayout.setVisibility(View.VISIBLE);
                }

                if (isPublicCall && adapter.getSelectedItemCount() < 2) {
                    clearButton.setVisibility(View.GONE);
                } else {
                    clearButton.setVisibility(View.VISIBLE);
                }
            } else {
                bottomButtonsLinearLayout.setVisibility(View.GONE);
            }
        } else if (bottomButtonsLinearLayout != null) {
            bottomButtonsLinearLayout.setVisibility(View.GONE);
        }

        if (bottomButtonsLinearLayout != null && bottomButtonsLinearLayout.getVisibility() == View.VISIBLE) {
            if (adapter.getScrollableFooters().size() == 0) {
                adapter.addScrollableFooterWithDelay(new EmptyFooterItem(999), 0, layoutManager
                        .findLastVisibleItemPosition() == adapter.getItemCount() - 1);
            }
        } else {
            if (adapter != null) {
                adapter.removeAllScrollableFooters();
            }
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
                RouterTransaction.with(new EntryMenuController(bundle))
                        .popChangeHandler(new VerticalChangeHandler())
                        .pushChangeHandler(new VerticalChangeHandler()));

        if (bottomSheet == null) {
            bottomSheet = new BottomSheet.Builder(getActivity()).setView(view).create();
        }

        bottomSheet.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

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
                    bottomSheet.cancel();
                }
            }
        }
    }

    @Override
    protected void onDetach(@NonNull View view) {
        super.onDetach(view);
        eventBus.unregister(this);
    }

}
