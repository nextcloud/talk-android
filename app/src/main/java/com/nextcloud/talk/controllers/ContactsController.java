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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import butterknife.Optional;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.bluelinelabs.logansquare.LoganSquare;
import com.kennyc.bottomsheet.BottomSheet;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.adapters.items.GenericTextHeaderItem;
import com.nextcloud.talk.adapters.items.ProgressItem;
import com.nextcloud.talk.adapters.items.UserItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.controllers.bottomsheet.EntryMenuController;
import com.nextcloud.talk.controllers.bottomsheet.OperationsMenuController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall;
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.models.json.sharees.Sharee;
import com.nextcloud.talk.models.json.sharees.ShareesOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.KeyboardUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import eu.davidea.fastscroller.FastScroller;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.SelectableAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.flexibleadapter.items.IFlexible;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.parceler.Parcels;

import javax.inject.Inject;
import java.util.*;

@AutoInjector(NextcloudTalkApplication.class)
public class ContactsController extends BaseController implements SearchView.OnQueryTextListener,
        FlexibleAdapter.OnItemClickListener, FastScroller.OnScrollStateChangeListener, FlexibleAdapter.EndlessScrollListener {

    public static final String TAG = "ContactsController";

    @Nullable
    @BindView(R.id.initial_relative_layout)
    RelativeLayout initialRelativeLayout;
    @Nullable
    @BindView(R.id.secondary_relative_layout)
    RelativeLayout secondaryRelativeLayout;
    @Inject
    UserUtils userUtils;
    @Inject
    EventBus eventBus;
    @Inject
    AppPreferences appPreferences;
    @BindView(R.id.progressBar)
    ProgressBar progressBar;
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;

    @BindView(R.id.fast_scroller)
    FastScroller fastScroller;

    @BindView(R.id.call_header_layout)
    RelativeLayout conversationPrivacyToogleLayout;

    @BindView(R.id.joinConversationViaLinkRelativeLayout)
    RelativeLayout joinConversationViaLinkLayout;

    @BindView(R.id.generic_rv_layout)
    CoordinatorLayout genericRvLayout;

    @Inject
    NcApi ncApi;
    private String credentials;
    private UserEntity currentUser;
    private Disposable contactsQueryDisposable;
    private Disposable cacheQueryDisposable;
    private FlexibleAdapter adapter;
    private List<AbstractFlexibleItem> contactItems;
    private BottomSheet bottomSheet;
    private View view;
    private int currentPage;
    private int currentSearchPage;

    private SmoothScrollLinearLayoutManager layoutManager;

    private MenuItem searchItem;
    private SearchView searchView;

    private boolean isNewConversationView;
    private boolean isPublicCall;

    private HashMap<String, GenericTextHeaderItem> userHeaderItems = new HashMap<>();

    private boolean alreadyFetching = false;
    private boolean canFetchFurther = true;
    private boolean canFetchSearchFurther = true;

    private MenuItem doneMenuItem;

    private Set<String> selectedUserIds;
    private Set<String> selectedGroupIds;
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

        selectedGroupIds = new HashSet<>();
        selectedUserIds = new HashSet<>();
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_contacts_rv, container, false);
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        eventBus.register(this);

        if (isNewConversationView) {
            toggleNewCallHeaderVisibility(!isPublicCall);
        }
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        currentUser = userUtils.getCurrentUser();

        if (currentUser != null) {
            credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());
        }

        if (adapter == null) {
            contactItems = new ArrayList<>();
            adapter = new FlexibleAdapter<>(contactItems, getActivity(), true);

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

        adapter.setStickyHeaderElevation(5)
                .setUnlinkAllItemsOnRemoveHeaders(true)
                .setDisplayHeadersAtStartUp(true)
                .setStickyHeaders(true);

        adapter.addListener(this);
    }

    private void selectionDone() {
        if (!isPublicCall && (selectedGroupIds.size() + selectedUserIds.size() == 1)) {
            String userId;
            String roomType = "1";

            if (selectedGroupIds.size() == 1) {
                roomType = "2";
                userId = selectedGroupIds.iterator().next();
            } else {
                userId = selectedUserIds.iterator().next();
            }

            RetrofitBucket retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(currentUser.getBaseUrl(), roomType,
                    userId, null);
            ncApi.createRoom(credentials,
                    retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<RoomOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(RoomOverall roomOverall) {
                            Intent conversationIntent = new Intent(getActivity(), MagicCallActivity.class);
                            Bundle bundle = new Bundle();
                            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, currentUser);
                            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.getOcs().getData().getToken());
                            bundle.putString(BundleKeys.KEY_ROOM_ID, roomOverall.getOcs().getData().getRoomId());

                            if (currentUser.hasSpreedCapabilityWithName("chat-v2")) {
                                bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION,
                                        Parcels.wrap(roomOverall.getOcs().getData()));
                                conversationIntent.putExtras(bundle);
                                getRouter().replaceTopController((RouterTransaction.with(new ChatController(bundle))
                                        .pushChangeHandler(new HorizontalChangeHandler())
                                        .popChangeHandler(new HorizontalChangeHandler())));
                            } else {
                                conversationIntent.putExtras(bundle);
                                startActivity(conversationIntent);
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!isDestroyed() && !isBeingDestroyed()) {
                                            getRouter().popCurrentController();
                                        }
                                    }
                                }, 100);
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
            Conversation.ConversationType roomType;
            if (isPublicCall) {
                roomType = Conversation.ConversationType.ROOM_PUBLIC_CALL;
            } else {
                roomType = Conversation.ConversationType.ROOM_GROUP_CALL;
            }

            ArrayList<String> userIdsArray = new ArrayList<>(selectedUserIds);
            ArrayList<String> groupIdsArray = new ArrayList<>(selectedGroupIds);


            bundle.putParcelable(BundleKeys.KEY_CONVERSATION_TYPE, Parcels.wrap(roomType));
            bundle.putStringArrayList(BundleKeys.KEY_INVITED_PARTICIPANTS, userIdsArray);
            bundle.putStringArrayList(BundleKeys.KEY_INVITED_GROUP, groupIdsArray);
            bundle.putInt(BundleKeys.KEY_OPERATION_CODE, 11);
            prepareAndShowBottomSheetWithBundle(bundle, true);
        }
    }

    private void initSearchView() {
        if (getActivity() != null) {
            SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
            if (searchItem != null) {
                searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
                searchView.setMaxWidth(Integer.MAX_VALUE);
                searchView.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER);
                int imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.getIsKeyboardIncognito()) {
                    imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
                }
                searchView.setImeOptions(imeOptions);
                searchView.setQueryHint(getResources().getString(R.string.nc_search));
                if (searchManager != null) {
                    searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
                }
                searchView.setOnQueryTextListener(this);
            }
        }
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
    public void onCreateOptionsMenu(Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_contacts, menu);
        searchItem = menu.findItem(R.id.action_search);
        doneMenuItem = menu.findItem(R.id.contacts_selection_done);

        initSearchView();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        checkAndHandleDoneMenuItem();
        if (adapter.hasFilter()) {
            searchItem.expandActionView();
            searchView.setQuery((CharSequence) adapter.getFilter(String.class), false);
        }
    }

    private void fetchData(boolean startFromScratch) {
        dispose(null);

        alreadyFetching = true;
        Set<Sharee> shareeHashSet = new HashSet<>();
        Set<AutocompleteUser> autocompleteUsersHashSet = new HashSet<>();

        userHeaderItems = new HashMap<>();

        String query = (String) adapter.getFilter(String.class);

        RetrofitBucket retrofitBucket;
        boolean serverIs14OrUp = false;
        if (currentUser.hasSpreedCapabilityWithName("last-room-activity")) {
            // a hack to see if we're on 14 or not
            retrofitBucket = ApiUtils.getRetrofitBucketForContactsSearchFor14(currentUser.getBaseUrl(), query);
            serverIs14OrUp = true;
        } else {
            retrofitBucket = ApiUtils.getRetrofitBucketForContactsSearch(currentUser.getBaseUrl(), query);
        }

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

        List<String> shareTypesList = null;

        if (serverIs14OrUp) {
            shareTypesList = new ArrayList<>();
            // users
            shareTypesList.add("0");
            // groups
            shareTypesList.add("1");
            // mails
            //shareTypesList.add("4");


            modifiedQueryMap.put("shareTypes[]", shareTypesList);
        }

        boolean finalServerIs14OrUp = serverIs14OrUp;
        ncApi.getContactsWithSearchParam(
                credentials,
                retrofitBucket.getUrl(), shareTypesList, modifiedQueryMap)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(3)
                .subscribe(new Observer<ResponseBody>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        contactsQueryDisposable = d;
                    }

                    @Override
                    public void onNext(ResponseBody responseBody) {
                        if (responseBody != null) {
                            Participant participant;

                            List<AbstractFlexibleItem> newUserItemList = new ArrayList<>();

                            try {
                                if (!finalServerIs14OrUp) {
                                    ShareesOverall shareesOverall = LoganSquare.parse(responseBody.string(), ShareesOverall.class);

                                    if (shareesOverall.getOcs().getData().getUsers() != null) {
                                        shareeHashSet.addAll(shareesOverall.getOcs().getData().getUsers());
                                    }

                                    if (shareesOverall.getOcs().getData().getExactUsers() != null &&
                                            shareesOverall.getOcs().getData().getExactUsers().getExactSharees() != null) {
                                        shareeHashSet.addAll(shareesOverall.getOcs().getData().
                                                getExactUsers().getExactSharees());
                                    }

                                    for (Sharee sharee : shareeHashSet) {
                                        if (!sharee.getValue().getShareWith().equals(currentUser.getUserId())) {
                                            participant = new Participant();
                                            participant.setDisplayName(sharee.getLabel());
                                            String headerTitle;

                                            headerTitle = sharee.getLabel().substring(0, 1).toUpperCase();

                                            GenericTextHeaderItem genericTextHeaderItem;
                                            if (!userHeaderItems.containsKey(headerTitle)) {
                                                genericTextHeaderItem = new GenericTextHeaderItem(headerTitle);
                                                userHeaderItems.put(headerTitle, genericTextHeaderItem);
                                            }

                                            participant.setUserId(sharee.getValue().getShareWith());

                                            UserItem newContactItem = new UserItem(participant, currentUser,
                                                    userHeaderItems.get(headerTitle));

                                            if (!contactItems.contains(newContactItem)) {
                                                newUserItemList.add(newContactItem);
                                            }

                                        }

                                    }

                                } else {
                                    AutocompleteOverall autocompleteOverall = LoganSquare.parse(responseBody.string(), AutocompleteOverall.class);
                                    autocompleteUsersHashSet.addAll(autocompleteOverall.getOcs().getData());

                                    for (AutocompleteUser autocompleteUser : autocompleteUsersHashSet) {
                                        if (!autocompleteUser.getId().equals(currentUser.getUserId())) {
                                            participant = new Participant();
                                            participant.setUserId(autocompleteUser.getId());
                                            participant.setDisplayName(autocompleteUser.getLabel());
                                            participant.setSource(autocompleteUser.getSource());

                                            String headerTitle;

                                            if (!autocompleteUser.getSource().equals("groups")) {
                                                headerTitle = participant.getDisplayName().substring(0, 1).toUpperCase();
                                            } else {
                                                headerTitle = getResources().getString(R.string.nc_groups);
                                            }

                                            GenericTextHeaderItem genericTextHeaderItem;
                                            if (!userHeaderItems.containsKey(headerTitle)) {
                                                genericTextHeaderItem = new GenericTextHeaderItem(headerTitle);
                                                userHeaderItems.put(headerTitle, genericTextHeaderItem);
                                            }


                                            UserItem newContactItem = new UserItem(participant, currentUser,
                                                    userHeaderItems.get(headerTitle));

                                            if (!contactItems.contains(newContactItem)) {
                                                newUserItemList.add(newContactItem);
                                            }

                                        }
                                    }
                                }
                            } catch (Exception exception) {
                                Log.e(TAG, "Parsing response body failed while getting contacts");
                            }

                            if (TextUtils.isEmpty((CharSequence) modifiedQueryMap.get("search"))) {
                                canFetchFurther = !shareeHashSet.isEmpty() || (finalServerIs14OrUp && autocompleteUsersHashSet.size() == 100);
                                currentPage = (int) modifiedQueryMap.get("page");
                            } else {
                                canFetchSearchFurther = !shareeHashSet.isEmpty() || (finalServerIs14OrUp && autocompleteUsersHashSet.size() == 100);
                                currentSearchPage = (int) modifiedQueryMap.get("page");
                            }

                            userHeaderItems = new HashMap<>();
                            contactItems.addAll(newUserItemList);

                            Collections.sort(newUserItemList, (o1, o2) -> {
                                String firstName;
                                String secondName;


                                if (o1 instanceof UserItem) {
                                    firstName = ((UserItem) o1).getModel().getDisplayName();
                                } else {
                                    firstName = ((GenericTextHeaderItem) o1).getModel();
                                }

                                if (o2 instanceof UserItem) {
                                    secondName = ((UserItem) o2).getModel().getDisplayName();
                                } else {
                                    secondName = ((GenericTextHeaderItem) o2).getModel();
                                }

                                if (o1 instanceof UserItem && o2 instanceof UserItem) {
                                    if ("groups".equals(((UserItem) o1).getModel().getSource()) && "groups".equals(((UserItem) o2).getModel().getSource())) {
                                        return firstName.compareToIgnoreCase(secondName);
                                    } else if ("groups".equals(((UserItem) o1).getModel().getSource())) {
                                        return -1;
                                    } else if ("groups".equals(((UserItem) o2).getModel().getSource())) {
                                        return 1;
                                    }
                                }

                                return firstName.compareToIgnoreCase(secondName);
                            });

                            Collections.sort(contactItems, (o1, o2) -> {
                                String firstName;
                                String secondName;


                                if (o1 instanceof UserItem) {
                                    firstName = ((UserItem) o1).getModel().getDisplayName();
                                } else {
                                    firstName = ((GenericTextHeaderItem) o1).getModel();
                                }

                                if (o2 instanceof UserItem) {
                                    secondName = ((UserItem) o2).getModel().getDisplayName();
                                } else {
                                    secondName = ((GenericTextHeaderItem) o2).getModel();
                                }

                                if (o1 instanceof UserItem && o2 instanceof UserItem) {
                                    if ("groups".equals(((UserItem) o1).getModel().getSource()) && "groups".equals(((UserItem) o2).getModel().getSource())) {
                                        return firstName.compareToIgnoreCase(secondName);
                                    } else if ("groups".equals(((UserItem) o1).getModel().getSource())) {
                                        return -1;
                                    } else if ("groups".equals(((UserItem) o2).getModel().getSource())) {
                                        return 1;
                                    }
                                }

                                return firstName.compareToIgnoreCase(secondName);
                            });


                            if (newUserItemList.size() > 0) {
                                adapter.updateDataSet(newUserItemList);
                            } else {
                                adapter.filterItems();
                            }

                            if (swipeRefreshLayout != null) {
                                swipeRefreshLayout.setRefreshing(false);
                            }
                        }

                    }

                    @Override
                    public void onError(Throwable e) {
                        if (swipeRefreshLayout != null) {
                            swipeRefreshLayout.setRefreshing(false);
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

                        disengageProgressBar();
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
            } else if (abstractFlexibleItem instanceof GenericTextHeaderItem) {
                return ((GenericTextHeaderItem) adapter.getItem(position)).getModel();
            } else {
                return "";
            }
        });

        disengageProgressBar();
    }

    private void disengageProgressBar() {
        if (!alreadyFetching) {
            progressBar.setVisibility(View.GONE);
            genericRvLayout.setVisibility(View.VISIBLE);

            if (isNewConversationView) {
                conversationPrivacyToogleLayout.setVisibility(View.VISIBLE);
                joinConversationViaLinkLayout.setVisibility(View.VISIBLE);
            }
        }
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
    }

    @Override
    public void onRestoreViewState(@NonNull View view, @NonNull Bundle savedViewState) {
        super.onRestoreViewState(view, savedViewState);
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
        if (!newText.equals("") && adapter.hasNewFilter(newText)) {
            adapter.setFilter(newText);
            fetchData(true);
        } else if (newText.equals("")) {
            adapter.setFilter("");
            adapter.updateDataSet(contactItems);
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
            if ((selectedGroupIds.size() + selectedUserIds.size() > 0) || isPublicCall) {
                doneMenuItem.setVisible(true);
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


    private void prepareAndShowBottomSheetWithBundle(Bundle bundle, boolean showEntrySheet) {
        if (view == null) {
            view = getActivity().getLayoutInflater().inflate(R.layout.bottom_sheet, null, false);
        }

        if (bottomSheet == null) {
            bottomSheet = new BottomSheet.Builder(getActivity()).setView(view).create();
        }

        if (showEntrySheet) {
            getChildRouter((ViewGroup) view).setRoot(
                    RouterTransaction.with(new EntryMenuController(bundle))
                            .popChangeHandler(new VerticalChangeHandler())
                            .pushChangeHandler(new VerticalChangeHandler()));
        } else {
            getChildRouter((ViewGroup) view).setRoot(
                    RouterTransaction.with(new OperationsMenuController(bundle))
                            .popChangeHandler(new VerticalChangeHandler())
                            .pushChangeHandler(new VerticalChangeHandler()));
        }

        bottomSheet.setOnShowListener(dialog -> {
            if (showEntrySheet) {
                new KeyboardUtils(getActivity(), bottomSheet.getLayout(), true);
            } else {
                eventBus.post(new BottomSheetLockEvent(false, 0,
                        false, false));
            }
        });

        bottomSheet.setOnDismissListener(dialog -> getActionBar().setDisplayHomeAsUpEnabled(getRouter().getBackstackSize() > 1));

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
                String roomType = "1";

                if ("groups".equals(userItem.getModel().getSource())) {
                    roomType = "2";
                }

                RetrofitBucket retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(currentUser.getBaseUrl(), roomType, userItem.getModel().getUserId(), null);

                ncApi.createRoom(credentials,
                        retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                        .subscribeOn(Schedulers.io())
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
                                    bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, currentUser);
                                    bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomOverall.getOcs().getData().getToken());
                                    bundle.putString(BundleKeys.KEY_ROOM_ID, roomOverall.getOcs().getData().getRoomId());
                                    conversationIntent.putExtras(bundle);

                                    if (currentUser.hasSpreedCapabilityWithName("chat-v2")) {
                                        bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION,
                                                Parcels.wrap(roomOverall.getOcs().getData()));
                                        getRouter().replaceTopController((RouterTransaction.with(new ChatController(bundle))
                                                .pushChangeHandler(new HorizontalChangeHandler())
                                                .popChangeHandler(new HorizontalChangeHandler())));
                                    } else {
                                        startActivity(conversationIntent);
                                        new Handler().postDelayed(() -> getRouter().popCurrentController(), 100);
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
                Participant participant = ((UserItem) adapter.getItem(position)).getModel();
                participant.setSelected(!participant.isSelected());

                if ("groups".equals(participant.getSource())) {
                    if (participant.isSelected()) {
                        selectedGroupIds.add(participant.getUserId());
                    } else {
                        selectedGroupIds.remove(participant.getUserId());
                    }
                } else {
                    if (participant.isSelected()) {
                        selectedUserIds.add(participant.getUserId());
                    } else {
                        selectedUserIds.remove(participant.getUserId());
                    }
                }

                if (currentUser.hasSpreedCapabilityWithName("last-room-activity")
                        && !currentUser.hasSpreedCapabilityWithName("invite-groups-and-mails") &&
                        "groups".equals(((UserItem) adapter.getItem(position)).getModel().getSource()) &&
                        participant.isSelected() &&
                        adapter.getSelectedItemCount() > 1) {
                    List<UserItem> currentItems = adapter.getCurrentItems();
                    Participant internalParticipant;
                    for (int i = 0; i < currentItems.size(); i++) {
                        internalParticipant = currentItems.get(i).getModel();
                        if (internalParticipant.getUserId().equals(participant.getUserId()) &&
                                "groups".equals(internalParticipant.getSource()) && internalParticipant.isSelected()) {
                            internalParticipant.setSelected(false);
                            selectedGroupIds.remove(internalParticipant.getUserId());
                        }
                    }

                }

                adapter.notifyDataSetChanged();
                checkAndHandleDoneMenuItem();
            }
        }
        return true;
    }

    @Optional
    @OnClick(R.id.joinConversationViaLinkRelativeLayout)
    void joinConversationViaLink() {
        Bundle bundle = new Bundle();
        bundle.putInt(BundleKeys.KEY_OPERATION_CODE, 10);

        prepareAndShowBottomSheetWithBundle(bundle, true);
    }

    @Optional
    @OnClick(R.id.call_header_layout)
    void toggleCallHeader() {
        toggleNewCallHeaderVisibility(isPublicCall);
        isPublicCall = !isPublicCall;

        if (isPublicCall) {
            List<AbstractFlexibleItem> currentItems = adapter.getCurrentItems();
            Participant internalParticipant;
            for (int i = 0; i < currentItems.size(); i++) {
                if (currentItems.get(i) instanceof UserItem) {
                    internalParticipant = ((UserItem) currentItems.get(i)).getModel();
                    if ("groups".equals(internalParticipant.getSource()) && internalParticipant.isSelected()) {
                        internalParticipant.setSelected(false);
                        selectedGroupIds.remove(internalParticipant.getUserId());
                    }
                }
            }
        }

        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (adapter.getItem(i) instanceof UserItem) {
                UserItem userItem = (UserItem) adapter.getItem(i);
                if ("groups".equals(userItem.getModel().getSource())) {
                    userItem.setEnabled(!isPublicCall);
                }
            }
        }

        adapter.notifyDataSetChanged();
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
        String query = (String) adapter.getFilter(String.class);

        if (!alreadyFetching && ((searchView != null && searchView.isIconified() && canFetchFurther)
                || (!TextUtils.isEmpty(query) && canFetchSearchFurther))) {
            fetchData(false);
        } else {
            adapter.onLoadMoreComplete(null);
        }
    }
}
