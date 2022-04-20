/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * @author Marcel Hibbe
 * Copyright (C) 2021 Andy Scherzinger (info@andy-scherzinger.de)
 * Copyright (C) 2017-2020 Mario Danic (mario@lovelyhq.com)
 * Copyright (C) 2022 Marcel Hibbe (dev@mhibbe.de)
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

import android.animation.AnimatorInflater;
import android.annotation.SuppressLint;
import android.app.SearchManager;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.request.ImageRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MainActivity;
import com.nextcloud.talk.adapters.items.ConversationItem;
import com.nextcloud.talk.adapters.items.GenericTextHeaderItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.ConversationsListFetchDataEvent;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.interfaces.ConversationMenuInterface;
import com.nextcloud.talk.jobs.AccountRemovalWorker;
import com.nextcloud.talk.jobs.ContactAddressBookWorker;
import com.nextcloud.talk.jobs.DeleteConversationWorker;
import com.nextcloud.talk.jobs.UploadAndShareFilesWorker;
import com.nextcloud.talk.models.database.CapabilitiesUtil;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.status.Status;
import com.nextcloud.talk.models.json.statuses.StatusesOverall;
import com.nextcloud.talk.ui.dialog.ChooseAccountDialogFragment;
import com.nextcloud.talk.ui.dialog.ConversationsListBottomDialog;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.ClosedInterfaceImpl;
import com.nextcloud.talk.utils.ConductorRemapping;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.UriUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.webianks.library.PopupBubble;
import com.yarolegovich.lovelydialog.LovelySaveStateHandler;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;

import org.apache.commons.lang3.builder.CompareToBuilder;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;

@AutoInjector(NextcloudTalkApplication.class)
public class ConversationsListController extends BaseController implements SearchView.OnQueryTextListener,
    FlexibleAdapter.OnItemClickListener, FlexibleAdapter.OnItemLongClickListener, ConversationMenuInterface {

    public static final String TAG = "ConvListController";
    public static final int ID_DELETE_CONVERSATION_DIALOG = 0;
    public static final int UNREAD_BUBBLE_DELAY = 2500;
    private static final String KEY_SEARCH_QUERY = "ContactsController.searchQuery";
    private final Bundle bundle;
    @Inject
    UserUtils userUtils;

    @Inject
    EventBus eventBus;

    @Inject
    NcApi ncApi;

    @Inject
    Context context;

    @Inject
    AppPreferences appPreferences;

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.swipeRefreshLayoutView)
    SwipeRefreshLayout swipeRefreshLayout;

    @BindView(R.id.loading_content)
    LinearLayout loadingContent;

    @BindView(R.id.emptyLayout)
    RelativeLayout emptyLayoutView;

    @BindView(R.id.floatingActionButton)
    FloatingActionButton floatingActionButton;

    @BindView(R.id.newMentionPopupBubble)
    PopupBubble newMentionPopupBubble;

    private UserEntity currentUser;
    private Disposable roomsQueryDisposable;
    private Disposable openConversationsQueryDisposable;
    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private List<AbstractFlexibleItem> conversationItems = new ArrayList<>();
    private List<AbstractFlexibleItem> conversationItemsWithHeader = new ArrayList<>();
    private final List<AbstractFlexibleItem> searchableConversationItems = new ArrayList<>();

    private MenuItem searchItem;
    private SearchView searchView;
    private String searchQuery;

    private String credentials;

    private boolean adapterWasNull = true;

    private boolean isRefreshing;

    private LovelySaveStateHandler saveStateHandler;

    private Bundle conversationMenuBundle = null;

    private boolean showShareToScreen = false;

    private ArrayList<String> filesToShare;
    private Conversation selectedConversation;

    private String textToPaste = "";

    private boolean forwardMessage;

    private int nextUnreadConversationScrollPosition = 0;

    private SmoothScrollLinearLayoutManager layoutManager;

    private HashMap<String, GenericTextHeaderItem> callHeaderItems = new HashMap<>();

    private ConversationsListBottomDialog conversationsListBottomDialog;

    private HashMap<String, Status> userStatuses = new HashMap<>();

    public ConversationsListController(Bundle bundle) {
        super();
        setHasOptionsMenu(true);
        forwardMessage = bundle.getBoolean(BundleKeys.INSTANCE.getKEY_FORWARD_MSG_FLAG());
        this.bundle = bundle;
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_conversations_rv, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        if (getActionBar() != null) {
            getActionBar().show();
        }

        if (saveStateHandler == null) {
            saveStateHandler = new LovelySaveStateHandler();
        }

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(conversationItems, getActivity(), true);
        } else {
            loadingContent.setVisibility(View.GONE);
        }

        adapter.addListener(this);
        prepareViews();
    }

    private void loadUserAvatar(MaterialButton button) {
        if (getActivity() != null) {
            ImageRequest imageRequest = DisplayUtils.getImageRequestForUrl(
                ApiUtils.getUrlForAvatar(
                    currentUser.getBaseUrl(),
                    currentUser.getUserId(),
                    false),
                currentUser);

            ImagePipeline imagePipeline = Fresco.getImagePipeline();
            DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);
            dataSource.subscribe(new BaseBitmapDataSubscriber() {
                @Override
                protected void onNewResultImpl(Bitmap bitmap) {
                    if (bitmap != null && getResources() != null) {
                        RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(getResources(), bitmap);
                        roundedBitmapDrawable.setCircular(true);
                        roundedBitmapDrawable.setAntiAlias(true);
                        button.setIcon(roundedBitmapDrawable);
                    }
                }

                @Override
                protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                    if (getResources() != null) {
                        button.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_user, null));
                    }
                }
            }, UiThreadImmediateExecutorService.getInstance());
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        Log.d(TAG, "onAttach: Controller: " + System.identityHashCode(this) +
            " Activity: " + System.identityHashCode(getActivity()));
        super.onAttach(view);

        new ClosedInterfaceImpl().setUpPushTokenRegistration();

        if (!eventBus.isRegistered(this)) {
            eventBus.register(this);
        }
        currentUser = userUtils.getCurrentUser();

        if (currentUser != null) {
            if (CapabilitiesUtil.isServerEOL(currentUser)) {
                showServerEOLDialog();
                return;
            }

            credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());
            if (getActivity() != null && getActivity() instanceof MainActivity) {
                loadUserAvatar(((MainActivity) getActivity()).binding.switchAccountButton);
            }
            fetchData();
        }
    }

    @Override
    protected void onDetach(@NonNull View view) {
        Log.d(TAG, "onDetach: Controller: " + System.identityHashCode(this) +
            " Activity: " + System.identityHashCode(getActivity()));
        super.onDetach(view);
        eventBus.unregister(this);
    }

    private void initSearchView() {
        if (getActivity() != null) {
            SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
            if (searchItem != null) {
                searchView = (SearchView) MenuItemCompat.getActionView(searchItem);
                DisplayUtils.themeSearchView(searchView, context);
                searchView.setMaxWidth(Integer.MAX_VALUE);
                searchView.setInputType(InputType.TYPE_TEXT_VARIATION_FILTER);
                int imeOptions = EditorInfo.IME_ACTION_DONE | EditorInfo.IME_FLAG_NO_FULLSCREEN;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && appPreferences.getIsKeyboardIncognito()) {
                    imeOptions |= EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING;
                }
                searchView.setImeOptions(imeOptions);
                searchView.setQueryHint(getSearchHint());
                if (searchManager != null) {
                    searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
                }
                searchView.setOnQueryTextListener(this);
            }
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_conversation_plus_filter, menu);
        searchItem = menu.findItem(R.id.action_search);
        initSearchView();
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);

        searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        showShareToScreen = !showShareToScreen && hasActivityActionSendIntent();


        if (showShareToScreen) {
            hideSearchBar();
            getActionBar().setTitle(R.string.send_to_three_dots);
        } else if (forwardMessage) {
            hideSearchBar();
            getActionBar().setTitle(R.string.nc_forward_to_three_dots);
        } else {
            MainActivity activity = (MainActivity) getActivity();

            searchItem.setVisible(conversationItems.size() > 0);
            if (activity != null) {
                if (adapter.hasFilter()) {
                    showSearchView(activity, searchView, searchItem);
                    searchView.setQuery(adapter.getFilter(String.class), false);
                }

                activity.binding.searchText.setOnClickListener(v -> {
                    showSearchView(activity, searchView, searchItem);
                    if (getResources() != null) {
                        DisplayUtils.applyColorToStatusBar(
                            activity,
                            ResourcesCompat.getColor(getResources(), R.color.appbar, null)
                                                          );
                    }
                });
            }

            searchView.setOnCloseListener(() -> {
                if (TextUtils.isEmpty(searchView.getQuery().toString())) {
                    searchView.onActionViewCollapsed();
                    if (activity != null && getResources() != null) {
                        DisplayUtils.applyColorToStatusBar(
                            activity,
                            ResourcesCompat.getColor(getResources(), R.color.bg_default, null)
                                                          );
                    }
                } else {
                    searchView.post(() -> searchView.setQuery(TAG, true));
                }
                return true;
            });

            searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
                @Override
                public boolean onMenuItemActionExpand(MenuItem item) {
                    adapter.setHeadersShown(true);
                    adapter.updateDataSet(searchableConversationItems, false);
                    adapter.showAllHeaders();
                    swipeRefreshLayout.setEnabled(false);
                    return true;
                }

                @Override
                public boolean onMenuItemActionCollapse(MenuItem item) {
                    adapter.setHeadersShown(false);
                    adapter.updateDataSet(conversationItems, false);
                    adapter.hideAllHeaders();
                    swipeRefreshLayout.setEnabled(true);

                    searchView.onActionViewCollapsed();
                    MainActivity activity = (MainActivity) getActivity();
                    if (activity != null) {
                        activity.binding.appBar.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                            activity.binding.appBar.getContext(),
                            R.animator.appbar_elevation_off)
                                                                    );
                        activity.binding.toolbar.setVisibility(View.GONE);
                        activity.binding.searchToolbar.setVisibility(View.VISIBLE);
                        if (getResources() != null) {
                            DisplayUtils.applyColorToStatusBar(
                                activity,
                                ResourcesCompat.getColor(getResources(), R.color.bg_default, null)
                                                              );
                        }
                    }
                    SmoothScrollLinearLayoutManager layoutManager =
                        (SmoothScrollLinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        layoutManager.scrollToPositionWithOffset(0, 0);
                    }
                    return true;
                }
            });
        }
    }

    private boolean hasActivityActionSendIntent() {
        if (getActivity() != null) {
            return Intent.ACTION_SEND.equals(getActivity().getIntent().getAction())
                || Intent.ACTION_SEND_MULTIPLE.equals(getActivity().getIntent().getAction());
        }
        return false;
    }

    @Override
    protected void showSearchOrToolbar() {
        if (TextUtils.isEmpty(searchQuery)) {
            super.showSearchOrToolbar();
        }
    }

    public void showSearchView(MainActivity activity, SearchView searchView, MenuItem searchItem) {
        activity.binding.appBar.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
            activity.binding.appBar.getContext(),
            R.animator.appbar_elevation_on));
        activity.binding.toolbar.setVisibility(View.VISIBLE);
        activity.binding.searchToolbar.setVisibility(View.GONE);
        searchItem.expandActionView();
    }

    @SuppressLint("LongLogTag")
    public void fetchData() {
        fetchUserStatuses();
    }

    private void fetchUserStatuses() {
        ncApi.getUserStatuses(credentials, ApiUtils.getUrlForUserStatuses(currentUser.getBaseUrl()))
            .subscribe(new Observer<StatusesOverall>() {
                @Override
                public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                }

                @Override
                public void onNext(@NonNull StatusesOverall statusesOverall) {
                    for (Status status : statusesOverall.getOcs().getData()) {
                        userStatuses.put(status.getUserId(), status);
                    }
                    fetchRooms();
                }

                @Override
                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                    Log.e(TAG, "failed to fetch user statuses", e);
                }

                @Override
                public void onComplete() {
                }
            });

    }

    private void fetchRooms() {
        dispose(null);

        isRefreshing = true;

        conversationItems = new ArrayList<>();
        conversationItemsWithHeader = new ArrayList<>();

        int apiVersion = ApiUtils.getConversationApiVersion(currentUser, new int[]{ApiUtils.APIv4, ApiUtils.APIv3, 1});

        long startNanoTime = System.nanoTime();
        Log.d(TAG, "fetchData - getRooms - calling: " + startNanoTime);
        roomsQueryDisposable = ncApi.getRooms(credentials, ApiUtils.getUrlForRooms(apiVersion,
                                                                                   currentUser.getBaseUrl()))
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(roomsOverall -> {
                Log.d(TAG, "fetchData - getRooms - got response: " + startNanoTime);

                // This is invoked asynchronously, when server returns a response the view might have been
                // unbound in the meantime. Check if the view is still there.
                // FIXME - does it make sense to update internal data structures even when view has been unbound?
                if (getView() == null) {
                    Log.d(TAG, "fetchData - getRooms - view is not bound: " + startNanoTime);
                    return;
                }

                if (adapterWasNull) {
                    adapterWasNull = false;
                    loadingContent.setVisibility(View.GONE);
                }

                if (roomsOverall.getOcs().getData().size() > 0) {
                    if (emptyLayoutView.getVisibility() != View.GONE) {
                        emptyLayoutView.setVisibility(View.GONE);
                    }

                    if (swipeRefreshLayout.getVisibility() != View.VISIBLE) {
                        swipeRefreshLayout.setVisibility(View.VISIBLE);
                    }
                } else {
                    if (emptyLayoutView.getVisibility() != View.VISIBLE) {
                        emptyLayoutView.setVisibility(View.VISIBLE);
                    }

                    if (swipeRefreshLayout.getVisibility() != View.GONE) {
                        swipeRefreshLayout.setVisibility(View.GONE);
                    }
                }

                for (Conversation conversation : roomsOverall.getOcs().getData()) {
                    if (bundle.containsKey(BundleKeys.INSTANCE.getKEY_FORWARD_HIDE_SOURCE_ROOM()) && conversation.roomId.equals(bundle.getString(
                        BundleKeys.INSTANCE.getKEY_FORWARD_HIDE_SOURCE_ROOM()))) {
                        continue;
                    }

                    String headerTitle;

                    headerTitle = getResources().getString(R.string.conversations);

                    GenericTextHeaderItem genericTextHeaderItem;
                    if (!callHeaderItems.containsKey(headerTitle)) {
                        genericTextHeaderItem = new GenericTextHeaderItem(headerTitle);
                        callHeaderItems.put(headerTitle, genericTextHeaderItem);
                    }

                    if (getActivity() != null) {
                        ConversationItem conversationItem = new ConversationItem(
                            conversation,
                            currentUser,
                            getActivity(),
                            userStatuses.get(conversation.name));
                        conversationItems.add(conversationItem);

                        ConversationItem conversationItemWithHeader = new ConversationItem(
                            conversation,
                            currentUser,
                            getActivity(),
                            callHeaderItems.get(headerTitle),
                            userStatuses.get(conversation.name));
                        conversationItemsWithHeader.add(conversationItemWithHeader);
                    }
                }

                sortConversations(conversationItems);
                sortConversations(conversationItemsWithHeader);

                adapter.updateDataSet(conversationItems, false);

                new Handler().postDelayed(this::checkToShowUnreadBubble, UNREAD_BUBBLE_DELAY);

                fetchOpenConversations(apiVersion);

                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }

            }, throwable -> {
                handleHttpExceptions(throwable);
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                dispose(roomsQueryDisposable);
            }, () -> {
                dispose(roomsQueryDisposable);
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }

                isRefreshing = false;
            });
    }

    private void sortConversations(List<AbstractFlexibleItem> conversationItems) {
        Collections.sort(conversationItems, (o1, o2) -> {
            Conversation conversation1 = ((ConversationItem) o1).getModel();
            Conversation conversation2 = ((ConversationItem) o2).getModel();
            return new CompareToBuilder()
                .append(conversation2.isFavorite(), conversation1.isFavorite())
                .append(conversation2.getLastActivity(), conversation1.getLastActivity())
                .toComparison();
        });
    }

    private void fetchOpenConversations(int apiVersion) {
        searchableConversationItems.clear();
        searchableConversationItems.addAll(conversationItemsWithHeader);

        if (CapabilitiesUtil.hasSpreedFeatureCapability(currentUser, "listable-rooms")) {
            List<AbstractFlexibleItem> openConversationItems = new ArrayList<>();

            openConversationsQueryDisposable = ncApi.getOpenConversations(
                credentials,
                ApiUtils.getUrlForOpenConversations(apiVersion, currentUser.getBaseUrl()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(roomsOverall -> {

                    for (Conversation conversation : roomsOverall.getOcs().getData()) {
                        String headerTitle = getResources().getString(R.string.openConversations);

                        GenericTextHeaderItem genericTextHeaderItem;
                        if (!callHeaderItems.containsKey(headerTitle)) {
                            genericTextHeaderItem = new GenericTextHeaderItem(headerTitle);
                            callHeaderItems.put(headerTitle, genericTextHeaderItem);
                        }

                        ConversationItem conversationItem = new ConversationItem(
                            conversation,
                            currentUser,
                            getActivity(),
                            callHeaderItems.get(headerTitle),
                            userStatuses.get(conversation.name));

                        openConversationItems.add(conversationItem);
                    }
                    searchableConversationItems.addAll(openConversationItems);

                }, throwable -> {
                    Log.e(TAG, "fetchData - getRooms - ERROR", throwable);
                    handleHttpExceptions(throwable);
                    dispose(openConversationsQueryDisposable);
                }, () -> {
                    dispose(openConversationsQueryDisposable);
                });
        } else {
            Log.d(TAG, "no open conversations fetched because of missing capability");
        }
    }

    private void handleHttpExceptions(Throwable throwable) {
        if (throwable instanceof HttpException) {
            HttpException exception = (HttpException) throwable;
            switch (exception.code()) {
                case 401:
                    if (getParentController() != null && getParentController().getRouter() != null) {
                        Log.d(TAG, "Starting reauth webview via getParentController()");
                        getParentController().getRouter().pushController((RouterTransaction.with
                            (new WebViewLoginController(currentUser.getBaseUrl(), true))
                            .pushChangeHandler(new VerticalChangeHandler())
                            .popChangeHandler(new VerticalChangeHandler())));
                    } else {
                        Log.d(TAG, "Starting reauth webview via ConversationsListController");
                        showUnauthorizedDialog();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void prepareViews() {
        layoutManager = new SmoothScrollLinearLayoutManager(Objects.requireNonNull(getActivity()));
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NotNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    checkToShowUnreadBubble();
                }
            }
        });

        recyclerView.setOnTouchListener((v, event) -> {
            InputMethodManager imm =
                (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return false;
        });

        swipeRefreshLayout.setOnRefreshListener(() -> fetchData());
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.refresh_spinner_background);

        emptyLayoutView.setOnClickListener(v -> showNewConversationsScreen());
        floatingActionButton.setOnClickListener(v -> {
            ContactAddressBookWorker.Companion.run(context);
            showNewConversationsScreen();
        });

        if (getActivity() != null && getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();

            activity.binding.switchAccountButton.setOnClickListener(v -> {
                if (getResources() != null && getResources().getBoolean(R.bool.multiaccount_support)) {
                    DialogFragment newFragment = ChooseAccountDialogFragment.newInstance();
                    newFragment.show(((MainActivity) getActivity()).getSupportFragmentManager(),
                                     "ChooseAccountDialogFragment");
                } else {
                    getRouter().pushController((RouterTransaction.with(new SettingsController())
                        .pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler())));
                }
            });
        }

        newMentionPopupBubble.hide();
        newMentionPopupBubble.setPopupBubbleListener(new PopupBubble.PopupBubbleClickListener() {
            @Override
            public void bubbleClicked(Context context) {
                recyclerView.smoothScrollToPosition(nextUnreadConversationScrollPosition);
            }
        });
    }

    private void checkToShowUnreadBubble() {
        try {
            int lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition();
            for (AbstractFlexibleItem flexItem : conversationItems) {
                Conversation conversationItem = ((ConversationItem) flexItem).getModel();
                int position = adapter.getGlobalPositionOf(flexItem);
                if ((conversationItem.unreadMention ||
                    (conversationItem.unreadMessages > 0 &&
                        conversationItem.type == Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL)) &&
                    position > lastVisibleItem) {
                    nextUnreadConversationScrollPosition = position;
                    if (!newMentionPopupBubble.isShown()) {
                        newMentionPopupBubble.show();
                    }
                    return;
                }
            }
            nextUnreadConversationScrollPosition = 0;
            newMentionPopupBubble.hide();
        } catch (NullPointerException e) {
            Log.d(TAG, "A NPE was caught when trying to show the unread popup bubble. This might happen when the " +
                "user already left the conversations-list screen so the popup bubble is not available anymore.", e);
        }
    }

    private void showNewConversationsScreen() {
        Bundle bundle = new Bundle();
        bundle.putBoolean(BundleKeys.INSTANCE.getKEY_NEW_CONVERSATION(), true);
        getRouter().pushController((RouterTransaction.with(new ContactsController(bundle))
            .pushChangeHandler(new HorizontalChangeHandler())
            .popChangeHandler(new HorizontalChangeHandler())));
    }

    private void dispose(@Nullable Disposable disposable) {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
            disposable = null;
        } else if (disposable == null &&
            roomsQueryDisposable != null && !roomsQueryDisposable.isDisposed()) {
            roomsQueryDisposable.dispose();
            roomsQueryDisposable = null;
        } else if (disposable == null &&
            openConversationsQueryDisposable != null && !openConversationsQueryDisposable.isDisposed()) {
            openConversationsQueryDisposable.dispose();
            openConversationsQueryDisposable = null;
        }
    }

    @Override
    public void onSaveViewState(@NonNull View view, @NonNull Bundle outState) {
        saveStateHandler.saveInstanceState(outState);

        if (searchView != null && !TextUtils.isEmpty(searchView.getQuery())) {
            outState.putString(KEY_SEARCH_QUERY, searchView.getQuery().toString());
        }

        super.onSaveViewState(view, outState);
    }

    @Override
    public void onRestoreViewState(@NonNull View view, @NonNull Bundle savedViewState) {
        super.onRestoreViewState(view, savedViewState);
        if (savedViewState.containsKey(KEY_SEARCH_QUERY)) {
            searchQuery = savedViewState.getString(KEY_SEARCH_QUERY, "");
        }
        if (LovelySaveStateHandler.wasDialogOnScreen(savedViewState)) {
            //Dialog won't be restarted automatically, so we need to call this method.
            //Each dialog knows how to restore its state
            showLovelyDialog(LovelySaveStateHandler.getSavedDialogId(savedViewState), savedViewState);
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
                searchQuery = "";
                adapter.filterItems();
            } else {
                adapter.setFilter(newText);
                adapter.filterItems(300);
            }
        }
        return true;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        return onQueryTextChange(query);
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_app_product_name);
    }

    @Override
    public boolean onItemClick(View view, int position) {
        try {
            selectedConversation = ((ConversationItem) Objects.requireNonNull(adapter.getItem(position))).getModel();
            if (selectedConversation != null && getActivity() != null) {
                if (showShareToScreen) {
                    handleSharedData();
                    showShareToScreen = false;
                } else if (forwardMessage) {
                    openConversation(bundle.getString(BundleKeys.INSTANCE.getKEY_FORWARD_MSG_TEXT()));
                    forwardMessage = false;
                } else {
                    openConversation();
                }
            }
        } catch (ClassCastException e) {
            Log.w(TAG, "failed to cast clicked item to ConversationItem. Most probably a heading was clicked. This is" +
                " just ignored.", e);
        }
        return true;
    }

    private void handleSharedData() {
        collectDataFromIntent();
        if (!textToPaste.isEmpty()) {
            openConversation(textToPaste);
        } else if (filesToShare != null && !filesToShare.isEmpty()) {
            showSendFilesConfirmDialog();
        } else {
            Toast.makeText(context, context.getResources().getString(R.string.nc_common_error_sorry), Toast.LENGTH_LONG).show();
        }
    }

    private void showSendFilesConfirmDialog() {
        if (UploadAndShareFilesWorker.Companion.isStoragePermissionGranted(context)) {
            StringBuilder fileNamesWithLineBreaks = new StringBuilder("\n");

            for (String file : filesToShare) {
                String filename = UriUtils.Companion.getFileName(Uri.parse(file), context);
                fileNamesWithLineBreaks.append(filename).append("\n");
            }

            String confirmationQuestion;
            if (filesToShare.size() == 1) {
                confirmationQuestion =
                    String.format(getResources().getString(R.string.nc_upload_confirm_send_single),
                                  selectedConversation.getDisplayName());
            } else {
                confirmationQuestion =
                    String.format(getResources().getString(R.string.nc_upload_confirm_send_multiple),
                                  selectedConversation.getDisplayName());
            }

            new LovelyStandardDialog(getActivity())
                .setPositiveButtonColorRes(R.color.nc_darkGreen)
                .setTitle(confirmationQuestion)
                .setMessage(fileNamesWithLineBreaks.toString())
                .setPositiveButton(R.string.nc_yes, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        upload();
                        openConversation();
                    }
                })
                .setNegativeButton(R.string.nc_no, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.d(TAG, "sharing files aborted");
                    }
                })
                .show();
        } else {
            UploadAndShareFilesWorker.Companion.requestStoragePermission(ConversationsListController.this);
        }
    }

    @Override
    public void onItemLongClick(int position) {
        if (showShareToScreen) {
            Log.d(TAG, "sharing to multiple rooms not yet implemented. onItemLongClick is ignored.");

        } else {
            Object clickedItem = adapter.getItem(position);
            if (clickedItem != null) {
                Conversation conversation = ((ConversationItem) clickedItem).getModel();
                conversationsListBottomDialog = new ConversationsListBottomDialog(
                    getActivity(),
                    this,
                    userUtils.getCurrentUser(),
                    conversation);
                conversationsListBottomDialog.show();
            }
        }
    }

    private void collectDataFromIntent() {
        filesToShare = new ArrayList<>();
        if (getActivity() != null && getActivity().getIntent() != null) {
            Intent intent = getActivity().getIntent();
            if (Intent.ACTION_SEND.equals(intent.getAction())
                || Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
                try {
                    String mimeType = intent.getType();
                    if ("text/plain".equals(mimeType) && (intent.getStringExtra(Intent.EXTRA_TEXT) != null)) {
                        // Share from Google Chrome sets text/plain MIME type, but also provides a content:// URI
                        // with a *screenshot* of the current page in getClipData().
                        // Here we assume that when sharing a web page the user would prefer to send the URL
                        // of the current page rather than a screenshot.
                        textToPaste = intent.getStringExtra(Intent.EXTRA_TEXT);
                    } else {
                        if (intent.getClipData() != null) {
                            for (int i = 0; i < intent.getClipData().getItemCount(); i++) {
                                ClipData.Item item = intent.getClipData().getItemAt(i);
                                if (item.getUri() != null) {
                                    filesToShare.add(item.getUri().toString());
                                } else if (item.getText() != null) {
                                    textToPaste = item.getText().toString();
                                    break;
                                } else {
                                    Log.w(TAG, "datatype not yet implemented for share-to");
                                }
                            }
                        } else {
                            filesToShare.add(intent.getData().toString());
                        }
                    }
                    if (filesToShare.isEmpty() && textToPaste.isEmpty()) {
                        Toast.makeText(context, context.getResources().getString(R.string.nc_common_error_sorry),
                                       Toast.LENGTH_LONG).show();
                        Log.e(TAG, "failed to get data from intent");
                    }
                } catch (Exception e) {
                    Toast.makeText(context, context.getResources().getString(R.string.nc_common_error_sorry),
                                   Toast.LENGTH_LONG).show();
                    Log.e(TAG, "Something went wrong when extracting data from intent");
                }
            }
        }
    }

    private void upload() {
        if (selectedConversation == null) {
            Toast.makeText(context, context.getResources().getString(R.string.nc_common_error_sorry),
                           Toast.LENGTH_LONG).show();
            Log.e(TAG, "not able to upload any files because conversation was null.");
            return;
        }

        try {
            String[] filesToShareArray = new String[filesToShare.size()];
            filesToShareArray = filesToShare.toArray(filesToShareArray);

            Data data = new Data.Builder()
                .putStringArray(UploadAndShareFilesWorker.DEVICE_SOURCEFILES, filesToShareArray)
                .putString(
                    UploadAndShareFilesWorker.NC_TARGETPATH,
                    CapabilitiesUtil.getAttachmentFolder(currentUser))
                .putString(UploadAndShareFilesWorker.ROOM_TOKEN, selectedConversation.getToken())
                .build();
            OneTimeWorkRequest uploadWorker = new OneTimeWorkRequest.Builder(UploadAndShareFilesWorker.class)
                .setInputData(data)
                .build();
            WorkManager.getInstance().enqueue(uploadWorker);

            Toast.makeText(
                context, context.getResources().getString(R.string.nc_upload_in_progess),
                Toast.LENGTH_LONG
                          ).show();

        } catch (IllegalArgumentException e) {
            Toast.makeText(context, context.getResources().getString(R.string.nc_upload_failed), Toast.LENGTH_LONG).show();
            Log.e(TAG, "Something went wrong when trying to upload file", e);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == UploadAndShareFilesWorker.REQUEST_PERMISSION &&
            grantResults.length > 0 &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "upload starting after permissions were granted");
            showSendFilesConfirmDialog();
        } else {
            Toast.makeText(context, context.getString(R.string.read_storage_no_permission), Toast.LENGTH_LONG).show();
        }
    }

    private void openConversation() {
        openConversation("");
    }

    private void openConversation(String textToPaste) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), currentUser);
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ACTIVE_CONVERSATION(), Parcels.wrap(selectedConversation));
        bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), selectedConversation.getToken());
        bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), selectedConversation.getRoomId());
        bundle.putString(BundleKeys.INSTANCE.getKEY_SHARED_TEXT(), textToPaste);

        ConductorRemapping.INSTANCE.remapChatController(getRouter(), currentUser.getId(),
                                                        selectedConversation.getToken(), bundle, false);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(EventStatus eventStatus) {
        if (currentUser != null && eventStatus.getUserId() == currentUser.getId()) {
            switch (eventStatus.getEventType()) {
                case CONVERSATION_UPDATE:
                    if (eventStatus.isAllGood() && !isRefreshing) {
                        fetchData();
                    }
                    break;
                default:
                    break;
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ConversationsListFetchDataEvent conversationsListFetchDataEvent) {
        fetchData();

        new Handler().postDelayed(() -> {
            if (conversationsListBottomDialog.isShowing()) {
                conversationsListBottomDialog.dismiss();
            }
        }, 2500);
    }

    private void showDeleteConversationDialog(Bundle savedInstanceState) {
        if (getActivity() != null && conversationMenuBundle != null && currentUser != null && conversationMenuBundle.getLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID()) == currentUser.getId()) {

            Conversation conversation =
                Parcels.unwrap(conversationMenuBundle.getParcelable(BundleKeys.INSTANCE.getKEY_ROOM()));

            if (conversation != null) {
                new LovelyStandardDialog(getActivity(), LovelyStandardDialog.ButtonLayout.HORIZONTAL)
                    .setTopColorRes(R.color.nc_darkRed)
                    .setIcon(DisplayUtils.getTintedDrawable(context.getResources(),
                                                            R.drawable.ic_delete_black_24dp, R.color.bg_default))
                    .setPositiveButtonColor(context.getResources().getColor(R.color.nc_darkRed))
                    .setTitle(R.string.nc_delete_call)
                    .setMessage(R.string.nc_delete_conversation_more)
                    .setPositiveButton(R.string.nc_delete, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            Data.Builder data = new Data.Builder();
                            data.putLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(),
                                         conversationMenuBundle.getLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID()));
                            data.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), conversation.getToken());
                            conversationMenuBundle = null;
                            deleteConversation(data.build());
                        }
                    })
                    .setNegativeButton(R.string.nc_cancel, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            conversationMenuBundle = null;
                        }
                    })
                    .setInstanceStateHandler(ID_DELETE_CONVERSATION_DIALOG, saveStateHandler)
                    .setSavedInstanceState(savedInstanceState)
                    .show();
            }
        }
    }

    private void showUnauthorizedDialog() {
        if (getActivity() != null) {

            new LovelyStandardDialog(getActivity(), LovelyStandardDialog.ButtonLayout.HORIZONTAL)
                .setTopColorRes(R.color.nc_darkRed)
                .setIcon(DisplayUtils.getTintedDrawable(context.getResources(),
                                                        R.drawable.ic_delete_black_24dp, R.color.bg_default))
                .setPositiveButtonColor(context.getResources().getColor(R.color.nc_darkRed))
                .setCancelable(false)
                .setTitle(R.string.nc_dialog_invalid_password)
                .setMessage(R.string.nc_dialog_reauth_or_delete)
                .setPositiveButton(R.string.nc_delete, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        boolean otherUserExists = userUtils.scheduleUserForDeletionWithId(currentUser.getId());

                        OneTimeWorkRequest accountRemovalWork = new OneTimeWorkRequest.Builder(AccountRemovalWorker.class).build();
                        WorkManager.getInstance().enqueue(accountRemovalWork);

                        if (otherUserExists && getView() != null) {
                            onViewBound(getView());
                            onAttach(getView());
                        } else if (!otherUserExists) {
                            getRouter().setRoot(RouterTransaction.with(
                                new ServerSelectionController())
                                                    .pushChangeHandler(new VerticalChangeHandler())
                                                    .popChangeHandler(new VerticalChangeHandler()));
                        }
                    }
                })
                .setNegativeButton(R.string.nc_settings_reauthorize, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        getRouter().pushController(RouterTransaction.with(
                            new WebViewLoginController(currentUser.getBaseUrl(), true))
                                                       .pushChangeHandler(new VerticalChangeHandler())
                                                       .popChangeHandler(new VerticalChangeHandler()));
                    }
                })
                .setInstanceStateHandler(ID_DELETE_CONVERSATION_DIALOG, saveStateHandler)
                .show();
        }
    }

    private void showServerEOLDialog() {
        new LovelyStandardDialog(getActivity(), LovelyStandardDialog.ButtonLayout.HORIZONTAL)
            .setTopColorRes(R.color.nc_darkRed)
            .setIcon(DisplayUtils.getTintedDrawable(context.getResources(),
                                                    R.drawable.ic_warning_white,
                                                    R.color.bg_default))
            .setPositiveButtonColor(context.getResources().getColor(R.color.nc_darkRed))
            .setCancelable(false)
            .setTitle(R.string.nc_settings_server_eol_title)
            .setMessage(R.string.nc_settings_server_eol)
            .setPositiveButton(R.string.nc_settings_remove_account, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean otherUserExists = userUtils.scheduleUserForDeletionWithId(currentUser.getId());

                    OneTimeWorkRequest accountRemovalWork = new OneTimeWorkRequest.Builder(AccountRemovalWorker.class).build();
                    WorkManager.getInstance().enqueue(accountRemovalWork);

                    if (otherUserExists && getView() != null) {
                        onViewBound(getView());
                        onAttach(getView());
                    } else if (!otherUserExists) {
                        getRouter().setRoot(RouterTransaction.with(
                            new ServerSelectionController())
                                                .pushChangeHandler(new VerticalChangeHandler())
                                                .popChangeHandler(new VerticalChangeHandler()));
                    }
                }
            })
            .setNegativeButton(R.string.nc_cancel, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (userUtils.hasMultipleUsers()) {
                        getRouter().pushController(RouterTransaction.with(new SwitchAccountController()));
                    } else {
                        getActivity().finishAffinity();
                        getActivity().finish();
                    }
                }
            })
            .setInstanceStateHandler(ID_DELETE_CONVERSATION_DIALOG, saveStateHandler)
            .show();
    }

    private void deleteConversation(Data data) {
        OneTimeWorkRequest deleteConversationWorker =
            new OneTimeWorkRequest.Builder(DeleteConversationWorker.class).setInputData(data).build();
        WorkManager.getInstance().enqueue(deleteConversationWorker);
    }

    private void showLovelyDialog(int dialogId, Bundle savedInstanceState) {
        switch (dialogId) {
            case ID_DELETE_CONVERSATION_DIALOG:
                showDeleteConversationDialog(savedInstanceState);
                break;
            default:
                break;
        }
    }

    @Override
    public void openLovelyDialogWithIdAndBundle(int dialogId, Bundle bundle) {
        conversationMenuBundle = bundle;
        switch (dialogId) {
            case ID_DELETE_CONVERSATION_DIALOG:
                showLovelyDialog(dialogId, null);
                break;
            default:
                break;
        }
    }

    @Override
    public AppBarLayoutType getAppBarLayoutType() {
        return AppBarLayoutType.SEARCH_BAR;
    }
}
