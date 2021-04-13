/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Andy Scherzinger
 * Copyright (C) 2021 Andy Scherzinger (info@andy-scherzinger.de)
 * Copyright (C) 2017-2020 Mario Danic (mario@lovelyhq.com)
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
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.core.view.MenuItemCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.conductor.changehandler.TransitionChangeHandlerCompat;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.bluelinelabs.conductor.internal.NoOpControllerChangeHandler;
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
import com.kennyc.bottomsheet.BottomSheet;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.activities.MainActivity;
import com.nextcloud.talk.adapters.items.CallItem;
import com.nextcloud.talk.adapters.items.ConversationItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.controllers.bottomsheet.CallMenuController;
import com.nextcloud.talk.controllers.bottomsheet.EntryMenuController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.events.EventStatus;
import com.nextcloud.talk.events.MoreMenuClickEvent;
import com.nextcloud.talk.interfaces.ConversationMenuInterface;
import com.nextcloud.talk.jobs.ContactAddressBookWorker;
import com.nextcloud.talk.jobs.DeleteConversationWorker;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.ConductorRemapping;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.KeyboardUtils;
import com.nextcloud.talk.utils.animations.SharedElementTransition;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.yarolegovich.lovelydialog.LovelySaveStateHandler;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;

import org.apache.commons.lang3.builder.CompareToBuilder;
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
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;

@AutoInjector(NextcloudTalkApplication.class)
public class ConversationsListController extends BaseController implements SearchView.OnQueryTextListener,
        FlexibleAdapter.OnItemClickListener, FlexibleAdapter.OnItemLongClickListener, FastScroller
                .OnScrollStateChangeListener, ConversationMenuInterface {

    public static final String TAG = "ConversationsListController";
    public static final int ID_DELETE_CONVERSATION_DIALOG = 0;
    private static final String KEY_SEARCH_QUERY = "ContactsController.searchQuery";
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

    @BindView(R.id.fast_scroller)
    FastScroller fastScroller;

    @BindView(R.id.floatingActionButton)
    FloatingActionButton floatingActionButton;

    private UserEntity currentUser;
    private Disposable roomsQueryDisposable;
    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private List<AbstractFlexibleItem> callItems = new ArrayList<>();

    private BottomSheet bottomSheet;
    private MenuItem searchItem;
    private SearchView searchView;
    private String searchQuery;

    private View view;
    private boolean shouldUseLastMessageLayout;

    private String credentials;

    private boolean adapterWasNull = true;

    private boolean isRefreshing;

    private LovelySaveStateHandler saveStateHandler;

    private Bundle conversationMenuBundle = null;

    public ConversationsListController() {
        super();
        setHasOptionsMenu(true);
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
            adapter = new FlexibleAdapter<>(callItems, getActivity(), true);
        } else {
            loadingContent.setVisibility(View.GONE);
        }

        adapter.addListener(this);
        prepareViews();
    }

    private void loadUserAvatar(MaterialButton button) {
        if (getActivity() != null) {
            int avatarSize;

            if (getResources() != null) {
                avatarSize = getResources().getDimensionPixelSize(R.dimen.avatar_size_app_bar);
            } else {
                avatarSize = (int) DisplayUtils.convertDpToPixel(30.0f, context);
            }

            ImageRequest imageRequest = DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithNameAndPixels(currentUser.getBaseUrl(),
                    currentUser.getUserId(), avatarSize), currentUser);

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
                        button.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_settings_white_24dp, null));
                    }
                }
            }, UiThreadImmediateExecutorService.getInstance());
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        eventBus.register(this);

        currentUser = userUtils.getCurrentUser();

        if (currentUser != null) {
            credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());
            shouldUseLastMessageLayout = currentUser.hasSpreedFeatureCapability("last-room-activity");
            if (getActivity() != null && getActivity() instanceof MainActivity) {
                loadUserAvatar(((MainActivity) getActivity()).settingsButton);
            }
            fetchData(false);
        }
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

        MainActivity activity = (MainActivity) getActivity();

        searchItem.setVisible(callItems.size() > 0);
        if (adapter.hasFilter()) {
            showSearchView(activity, searchView, searchItem);
            searchView.setQuery(adapter.getFilter(String.class), false);
        }

        if (activity != null) {
            activity.getSearchInputText().setOnClickListener(v -> {
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
                searchView.post(() -> searchView.setQuery("", true));
            }
            return true;
        });

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                searchView.onActionViewCollapsed();
                MainActivity activity = (MainActivity) getActivity();
                if (activity != null) {
                    activity.appBar.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                            activity.appBar.getContext(),
                            R.animator.appbar_elevation_off)
                    );
                    activity.toolbar.setVisibility(View.GONE);
                    activity.searchCardView.setVisibility(View.VISIBLE);
                    if (getResources() != null) {
                        DisplayUtils.applyColorToStatusBar(
                                activity,
                                ResourcesCompat.getColor(getResources(), R.color.bg_default, null)
                        );
                    }
                }
                SmoothScrollLinearLayoutManager layoutManager =
                        (SmoothScrollLinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager!=null) {
                    layoutManager.scrollToPositionWithOffset(0, 0);
                }
                return true;
            }
        });
    }

    protected void showSearchOrToolbar() {
        if (TextUtils.isEmpty(searchQuery)) {
            super.showSearchOrToolbar();
        }
    }

    public void showSearchView(MainActivity activity, SearchView searchView, MenuItem searchItem) {
        activity.appBar.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                activity.appBar.getContext(),
                R.animator.appbar_elevation_on));
        activity.toolbar.setVisibility(View.VISIBLE);
        activity.searchCardView.setVisibility(View.GONE);
        searchItem.expandActionView();
    }

    private void fetchData(boolean fromBottomSheet) {
        dispose(null);

        isRefreshing = true;

        callItems = new ArrayList<>();

        roomsQueryDisposable = ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(currentUser.getBaseUrl()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(roomsOverall -> {

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

                    Conversation conversation;
                    for (int i = 0; i < roomsOverall.getOcs().getData().size(); i++) {
                        conversation = roomsOverall.getOcs().getData().get(i);
                        if (shouldUseLastMessageLayout) {
                            if (getActivity() != null) {
                                ConversationItem conversationItem = new ConversationItem(conversation
                                        , currentUser, getActivity());
                                callItems.add(conversationItem);
                            }
                        } else {
                            CallItem callItem = new CallItem(conversation, currentUser);
                            callItems.add(callItem);
                        }
                    }

                    if (currentUser.hasSpreedFeatureCapability("last-room-activity")) {
                        Collections.sort(callItems, (o1, o2) -> {
                            Conversation conversation1 = ((ConversationItem) o1).getModel();
                            Conversation conversation2 = ((ConversationItem) o2).getModel();
                            return new CompareToBuilder()
                                    .append(conversation2.isFavorite(), conversation1.isFavorite())
                                    .append(conversation2.getLastActivity(), conversation1.getLastActivity())
                                    .toComparison();
                        });
                    } else {
                        Collections.sort(callItems, (callItem, t1) ->
                                Long.compare(((CallItem) t1).getModel().getLastPing(),
                                        ((CallItem) callItem).getModel().getLastPing()));
                    }

                    adapter.updateDataSet(callItems, false);

                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }

                }, throwable -> {
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

                    isRefreshing = false;
                });
    }

    private void prepareViews() {
        SmoothScrollLinearLayoutManager layoutManager =
                new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setOnRefreshListener(() -> fetchData(false));
        swipeRefreshLayout.setColorSchemeResources(R.color.colorPrimary);

        emptyLayoutView.setOnClickListener(v -> showNewConversationsScreen());
        floatingActionButton.setOnClickListener(v -> {
            ContactAddressBookWorker.Companion.run(context);
            showNewConversationsScreen();
        });

        fastScroller.addOnScrollStateChangeListener(this);
        adapter.setFastScroller(fastScroller);

        fastScroller.setBubbleTextCreator(position -> {
            String displayName;
            if (shouldUseLastMessageLayout) {
                displayName = ((ConversationItem) adapter.getItem(position)).getModel().getDisplayName();
            } else {
                displayName = ((CallItem) adapter.getItem(position)).getModel().getDisplayName();
            }

            if (displayName.length() > 8) {
                displayName = displayName.substring(0, 4) + "...";
            }
            return displayName;
        });

        if (getActivity() != null && getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();

            if (activity.settingsButton != null) {
                activity.settingsButton.setOnClickListener(v -> {
                    ArrayList<String> names = new ArrayList<>();
                    names.add("userAvatar.transitionTag");
                    getRouter().pushController((RouterTransaction.with(new SettingsController())
                            .pushChangeHandler(new TransitionChangeHandlerCompat(new SharedElementTransition(names), new VerticalChangeHandler()))
                            .popChangeHandler(new TransitionChangeHandlerCompat(new SharedElementTransition(names), new VerticalChangeHandler()))));
                });
            }
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
        Conversation conversation = moreMenuClickEvent.getConversation();
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ROOM(), Parcels.wrap(conversation));
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_MENU_TYPE(), Parcels.wrap(CallMenuController.MenuType.REGULAR));

        prepareAndShowBottomSheetWithBundle(bundle, true);
    }

    private void prepareAndShowBottomSheetWithBundle(Bundle bundle, boolean shouldShowCallMenuController) {
        if (view == null) {
            view = getActivity().getLayoutInflater().inflate(R.layout.bottom_sheet, null, false);
        }

        if (shouldShowCallMenuController) {
            getChildRouter((ViewGroup) view).setRoot(
                    RouterTransaction.with(new CallMenuController(bundle, this))
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
        bottomSheet.setOnDismissListener(dialog -> getActionBar().setDisplayHomeAsUpEnabled(getRouter().getBackstackSize() > 1));
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
        Object clickedItem = adapter.getItem(position);
        if (clickedItem != null && getActivity() != null) {
            Conversation conversation;
            if (shouldUseLastMessageLayout) {
                conversation = ((ConversationItem) clickedItem).getModel();
            } else {
                conversation = ((CallItem) clickedItem).getModel();
            }

            Bundle bundle = new Bundle();
            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), currentUser);
            bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), conversation.getToken());
            bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), conversation.getRoomId());

            if (conversation.hasPassword && (conversation.participantType.equals(Participant.ParticipantType.GUEST) ||
                    conversation.participantType.equals(Participant.ParticipantType.USER_FOLLOWING_LINK))) {
                bundle.putInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), 99);
                prepareAndShowBottomSheetWithBundle(bundle, false);
            } else {
                currentUser = userUtils.getCurrentUser();

                if (currentUser.hasSpreedFeatureCapability("chat-v2")) {
                    bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ACTIVE_CONVERSATION(), Parcels.wrap(conversation));
                    ConductorRemapping.INSTANCE.remapChatController(getRouter(), currentUser.getId(),
                            conversation.getToken(), bundle, false);
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

    @Override
    public void onItemLongClick(int position) {
        if (currentUser.hasSpreedFeatureCapability("last-room-activity")) {
            Object clickedItem = adapter.getItem(position);
            if (clickedItem != null) {
                Conversation conversation;
                if (shouldUseLastMessageLayout) {
                    conversation = ((ConversationItem) clickedItem).getModel();
                } else {
                    conversation = ((CallItem) clickedItem).getModel();
                }

                MoreMenuClickEvent moreMenuClickEvent = new MoreMenuClickEvent(conversation);
                onMessageEvent(moreMenuClickEvent);
            }
        }
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.BACKGROUND)
    public void onMessageEvent(EventStatus eventStatus) {
        if (currentUser != null && eventStatus.getUserId() == currentUser.getId()) {
            switch (eventStatus.getEventType()) {
                case CONVERSATION_UPDATE:
                    if (eventStatus.isAllGood() && !isRefreshing) {
                        fetchData(false);
                    }
                    break;
                default:
                    break;
            }
        }
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
                        .setMessage(conversation.getDeleteWarningMessage())
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
