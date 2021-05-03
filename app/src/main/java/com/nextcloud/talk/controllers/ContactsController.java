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
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.bluelinelabs.logansquare.LoganSquare;
import com.kennyc.bottomsheet.BottomSheet;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.adapters.items.GenericTextHeaderItem;
import com.nextcloud.talk.adapters.items.UserItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.controllers.bottomsheet.EntryMenuController;
import com.nextcloud.talk.controllers.bottomsheet.OperationsMenuController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.jobs.AddParticipantsToConversation;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall;
import com.nextcloud.talk.models.json.autocomplete.AutocompleteUser;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.ConductorRemapping;
import com.nextcloud.talk.utils.KeyboardUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
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
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.ResponseBody;

@AutoInjector(NextcloudTalkApplication.class)
public class ContactsController extends BaseController implements SearchView.OnQueryTextListener,
        FlexibleAdapter.OnItemClickListener, FastScroller.OnScrollStateChangeListener {

    public static final String TAG = "ContactsController";

    @Nullable
    @BindView(R.id.initial_relative_layout)
    RelativeLayout initialRelativeLayout;

    @Nullable
    @BindView(R.id.secondary_relative_layout)
    RelativeLayout secondaryRelativeLayout;

    @BindView(R.id.loading_content)
    LinearLayout loadingContent;

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

    @BindView(R.id.joinConversationViaLinkImageView)
    ImageView joinConversationViaLinkImageView;

    @BindView(R.id.public_call_link)
    ImageView publicCallLinkImageView;

    @BindView(R.id.generic_rv_layout)
    CoordinatorLayout genericRvLayout;

    @Inject
    NcApi ncApi;

    @Inject
    UserUtils userUtils;

    @Inject
    EventBus eventBus;

    @Inject
    AppPreferences appPreferences;

    private String credentials;
    private UserEntity currentUser;
    private Disposable contactsQueryDisposable;
    private Disposable cacheQueryDisposable;
    private FlexibleAdapter adapter;
    private List<AbstractFlexibleItem> contactItems;
    private BottomSheet bottomSheet;
    private View view;

    private SmoothScrollLinearLayoutManager layoutManager;

    private MenuItem searchItem;
    private SearchView searchView;

    private boolean isNewConversationView;
    private boolean isPublicCall;

    private HashMap<String, GenericTextHeaderItem> userHeaderItems = new HashMap<>();

    private boolean alreadyFetching = false;

    private MenuItem doneMenuItem;

    private Set<String> selectedUserIds;
    private Set<String> selectedGroupIds;
    private List<String> existingParticipants;
    private boolean isAddingParticipantsView;
    private String conversationToken;

    public ContactsController() {
        super();
        setHasOptionsMenu(true);
    }

    public ContactsController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        if (args.containsKey(BundleKeys.INSTANCE.getKEY_NEW_CONVERSATION())) {
            isNewConversationView = true;
            existingParticipants = new ArrayList<>();
        } else if (args.containsKey(BundleKeys.INSTANCE.getKEY_ADD_PARTICIPANTS())) {
            isAddingParticipantsView = true;
            conversationToken = args.getString(BundleKeys.INSTANCE.getKEY_TOKEN());

            existingParticipants = new ArrayList<>();

            if (args.containsKey(BundleKeys.INSTANCE.getKEY_EXISTING_PARTICIPANTS())) {
                existingParticipants = args.getStringArrayList(BundleKeys.INSTANCE.getKEY_EXISTING_PARTICIPANTS());
            }
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

        if (isAddingParticipantsView) {
            joinConversationViaLinkLayout.setVisibility(View.GONE);
            conversationPrivacyToogleLayout.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        currentUser = userUtils.getCurrentUser();

        if (currentUser != null) {
            credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());
        }

        if (adapter == null) {
            contactItems = new ArrayList<>();
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

        adapter.setStickyHeaderElevation(5)
                .setUnlinkAllItemsOnRemoveHeaders(true)
                .setDisplayHeadersAtStartUp(true)
                .setStickyHeaders(true);

        adapter.addListener(this);
    }

    private void selectionDone() {
        if (!isAddingParticipantsView) {
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
                                bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), currentUser);
                                bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), roomOverall.getOcs().getData().getToken());
                                bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), roomOverall.getOcs().getData().getRoomId());

                                if (currentUser.hasSpreedFeatureCapability("chat-v2")) {
                                    ncApi.getRoom(credentials,
                                            ApiUtils.getRoom(currentUser.getBaseUrl(),
                                                    roomOverall.getOcs().getData().getToken()))
                                            .subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(new Observer<RoomOverall>() {

                                                @Override
                                                public void onSubscribe(Disposable d) {

                                                }

                                                @Override
                                                public void onNext(RoomOverall roomOverall) {
                                                    bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ACTIVE_CONVERSATION(),
                                                            Parcels.wrap(roomOverall.getOcs().getData()));

                                                    ConductorRemapping.INSTANCE.remapChatController(getRouter(), currentUser.getId(),
                                                            roomOverall.getOcs().getData().getToken(), bundle, true);
                                                }

                                                @Override
                                                public void onError(Throwable e) {

                                                }

                                                @Override
                                                public void onComplete() {

                                                }
                                            });
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


                bundle.putParcelable(BundleKeys.INSTANCE.getKEY_CONVERSATION_TYPE(), Parcels.wrap(roomType));
                bundle.putStringArrayList(BundleKeys.INSTANCE.getKEY_INVITED_PARTICIPANTS(), userIdsArray);
                bundle.putStringArrayList(BundleKeys.INSTANCE.getKEY_INVITED_GROUP(), groupIdsArray);
                bundle.putInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), 11);
                prepareAndShowBottomSheetWithBundle(bundle, true);
            }
        } else {
            String[] userIdsArray = selectedUserIds.toArray(new String[selectedUserIds.size()]);
            String[] groupIdsArray = selectedGroupIds.toArray(new String[selectedGroupIds.size()]);

            Data.Builder data = new Data.Builder();
            data.putLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), currentUser.getId());
            data.putString(BundleKeys.INSTANCE.getKEY_TOKEN(), conversationToken);
            data.putStringArray(BundleKeys.INSTANCE.getKEY_SELECTED_USERS(), userIdsArray);
            data.putStringArray(BundleKeys.INSTANCE.getKEY_SELECTED_GROUPS(), groupIdsArray);

            OneTimeWorkRequest addParticipantsToConversationWorker =
                    new OneTimeWorkRequest.Builder(AddParticipantsToConversation.class).setInputData(data.build()).build();
            WorkManager.getInstance().enqueue(addParticipantsToConversationWorker);

            getRouter().popCurrentController();
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
        Set<AutocompleteUser> autocompleteUsersHashSet = new HashSet<>();

        userHeaderItems = new HashMap<>();

        String query = (String) adapter.getFilter(String.class);

        RetrofitBucket retrofitBucket = ApiUtils.getRetrofitBucketForContactsSearchFor14(currentUser.getBaseUrl(), query);
        Map<String, Object> modifiedQueryMap = new HashMap<String, Object>(retrofitBucket.getQueryMap());
        modifiedQueryMap.put("limit", 50);

        if (isAddingParticipantsView) {
            modifiedQueryMap.put("itemId", conversationToken);
        }

        List<String> shareTypesList = null;

            shareTypesList = new ArrayList<>();
            // users
            shareTypesList.add("0");
            // groups
            shareTypesList.add("1");

            modifiedQueryMap.put("shareTypes[]", shareTypesList);

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
                                    AutocompleteOverall autocompleteOverall = LoganSquare.parse(responseBody.string(), AutocompleteOverall.class);
                                    autocompleteUsersHashSet.addAll(autocompleteOverall.getOcs().getData());

                                    for (AutocompleteUser autocompleteUser : autocompleteUsersHashSet) {
                                        if (!autocompleteUser.getId().equals(currentUser.getUserId()) && !existingParticipants.contains(autocompleteUser.getId())) {
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
                            } catch (Exception exception) {
                                Log.e(TAG, "Parsing response body failed while getting contacts");
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
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.refresh_spinner_background);

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

        joinConversationViaLinkImageView
                .getBackground()
                .setColorFilter(ResourcesCompat.getColor(getResources(), R.color.colorBackgroundDarker, null),
                                PorterDuff.Mode.SRC_IN);

        publicCallLinkImageView
                .getBackground()
                .setColorFilter(ResourcesCompat.getColor(getResources(), R.color.colorBackgroundDarker, null),
                                PorterDuff.Mode.SRC_IN);

        disengageProgressBar();
    }

    private void disengageProgressBar() {
        if (!alreadyFetching) {
            loadingContent.setVisibility(View.GONE);
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
        if (!isNewConversationView && !isAddingParticipantsView) {
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
            if (!isNewConversationView && !isAddingParticipantsView) {
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
                                    bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), currentUser);
                                    bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), roomOverall.getOcs().getData().getToken());
                                    bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), roomOverall.getOcs().getData().getRoomId());
                                    conversationIntent.putExtras(bundle);

                                    if (currentUser.hasSpreedFeatureCapability("chat-v2")) {
                                        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ACTIVE_CONVERSATION(),
                                                Parcels.wrap(roomOverall.getOcs().getData()));

                                        ConductorRemapping.INSTANCE.remapChatController(getRouter(), currentUser.getId(),
                                                roomOverall.getOcs().getData().getToken(), bundle, true);
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

                if (currentUser.hasSpreedFeatureCapability("last-room-activity")
                        && !currentUser.hasSpreedFeatureCapability("invite-groups-and-mails") &&
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
        bundle.putInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), 10);

        prepareAndShowBottomSheetWithBundle(bundle, true);
    }

    @Optional
    @OnClick(R.id.call_header_layout)
    void toggleCallHeader() {
        toggleNewCallHeaderVisibility(isPublicCall);
        isPublicCall = !isPublicCall;

        if (isPublicCall) {
            joinConversationViaLinkLayout.setVisibility(View.GONE);
        } else {
            joinConversationViaLinkLayout.setVisibility(View.VISIBLE);
        }

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

        checkAndHandleDoneMenuItem();
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
}
