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
 */

package com.nextcloud.talk.controllers.bottomsheet;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.kennyc.bottomsheet.adapters.AppAdapter;
import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.items.AppItem;
import com.nextcloud.talk.adapters.items.MenuItem;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.ConversationsListController;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.interfaces.ConversationMenuInterface;
import com.nextcloud.talk.jobs.LeaveConversationWorker;
import com.nextcloud.talk.models.database.CapabilitiesUtil;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.ShareUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;

import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcel;
import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;

@AutoInjector(NextcloudTalkApplication.class)
public class CallMenuController extends BaseController implements FlexibleAdapter.OnItemClickListener {
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @Inject
    EventBus eventBus;

    @Inject
    UserUtils userUtils;

    @Inject
    Context context;

    private Conversation conversation;
    private List<AbstractFlexibleItem> menuItems;
    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private MenuType menuType;
    private Intent shareIntent;

    private UserEntity currentUser;
    private ConversationMenuInterface conversationMenuInterface;

    public CallMenuController(Bundle args) {
        super(args);
        this.conversation = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_ROOM()));
        if (args.containsKey(BundleKeys.INSTANCE.getKEY_MENU_TYPE())) {
            this.menuType = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_MENU_TYPE()));
        }
    }

    public CallMenuController(Bundle args, @Nullable ConversationMenuInterface conversationMenuInterface) {
        super(args);
        this.conversation = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_ROOM()));
        if (args.containsKey(BundleKeys.INSTANCE.getKEY_MENU_TYPE())) {
            this.menuType = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_MENU_TYPE()));
        }
        this.conversationMenuInterface = conversationMenuInterface;
    }

    @Override
    @NonNull
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_call_menu, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        prepareViews();
    }

    private void prepareViews() {
        LinearLayoutManager layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);

        prepareMenu();
        if (adapter == null) {
            adapter = new FlexibleAdapter<>(menuItems, getActivity(), false);
        }

        adapter.addListener(this);
        recyclerView.setAdapter(adapter);
    }

    private void prepareIntent() {
        shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, String.format(getResources().getString(R.string.nc_share_subject),
                getResources().getString(R.string.nc_app_product_name)));
    }

    private void prepareMenu() {
        menuItems = new ArrayList<>();

        if (menuType.equals(MenuType.REGULAR)) {
            if (!TextUtils.isEmpty(conversation.getDisplayName())) {
                menuItems.add(new MenuItem(conversation.getDisplayName(), 0, null));
            } else if (!TextUtils.isEmpty(conversation.getName())) {
                menuItems.add(new MenuItem(conversation.getName(), 0, null));
            } else {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_configure_room), 0, null));
            }

            currentUser = userUtils.getCurrentUser();

            if (conversation.isFavorite()) {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_remove_from_favorites), 97, DisplayUtils.getTintedDrawable(getResources(), R.drawable.ic_star_border_black_24dp, R.color.grey_600)));
            } else if (CapabilitiesUtil.hasSpreedFeatureCapability(currentUser, "favorites")) {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_add_to_favorites),
                                           98,
                                           DisplayUtils.getTintedDrawable(getResources(),
                                                                          R.drawable.ic_star_black_24dp,
                                                                          R.color.grey_600)));
            }

            if (conversation.isNameEditable(currentUser)) {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_rename),
                                           2,
                                           ContextCompat.getDrawable(context,
                                                                     R.drawable.ic_pencil_grey600_24dp)));
            }

            if (conversation.canModerate(currentUser)) {
                if (!conversation.isPublic()) {
                    menuItems.add(new MenuItem(getResources().getString(R.string.nc_make_call_public),
                                               3, ContextCompat.getDrawable(context,
                                                                            R.drawable.ic_link_grey600_24px)));
                } else {
                    if (conversation.isHasPassword()) {
                        menuItems.add(new MenuItem(getResources().getString(R.string.nc_change_password),
                                                   4, ContextCompat.getDrawable(context,
                                                                                R.drawable.ic_lock_grey600_24px)));
                        menuItems.add(new MenuItem(getResources().getString(R.string.nc_clear_password),
                                                   5,
                                                   ContextCompat.getDrawable(context,
                                                                             R.drawable.ic_lock_open_grey600_24dp)));
                    } else {
                        menuItems.add(new MenuItem(getResources().getString(R.string.nc_set_password),
                                                   6, ContextCompat.getDrawable(context,
                                                                                R.drawable.ic_lock_plus_grey600_24dp)));
                    }
                }

                menuItems.add(new MenuItem(getResources().getString(R.string.nc_delete_call),
                                           9, ContextCompat.getDrawable(context,
                                                                        R.drawable.ic_delete_grey600_24dp)));
            }

            if (conversation.isPublic()) {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_share_link),
                                           7, ContextCompat.getDrawable(context,
                                                                        R.drawable.ic_link_grey600_24px)));
                if (conversation.canModerate(currentUser)) {
                    menuItems.add(new MenuItem(getResources().getString(R.string.nc_make_call_private),
                                               8, ContextCompat.getDrawable(context,
                                                                            R.drawable.ic_group_grey600_24px)));
                }
            }

            if (conversation.canLeave(currentUser)) {
                menuItems.add(new MenuItem(getResources().getString(R.string.nc_leave), 1,
                        DisplayUtils.getTintedDrawable(getResources(),
                                R.drawable.ic_exit_to_app_black_24dp, R.color.grey_600)
                ));
            }
        } else if (menuType.equals(MenuType.SHARE)) {
            prepareIntent();
            List<AppAdapter.AppInfo> appInfoList = ShareUtils.getShareApps(getActivity(), shareIntent, null,
                    null);
            menuItems.add(new AppItem(getResources().getString(R.string.nc_share_link_via),
                                      "",
                                      "",
                                      ContextCompat.getDrawable(context, R.drawable.ic_link_grey600_24px)));
            if (appInfoList != null) {
                for (AppAdapter.AppInfo appInfo : appInfoList) {
                    menuItems.add(new AppItem(appInfo.title, appInfo.packageName, appInfo.name, appInfo.drawable));
                }
            }
        } else {
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_start_conversation), 0, null));
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_new_conversation),
                                       1, ContextCompat.getDrawable(context,
                                                                    R.drawable.ic_add_grey600_24px)));
            menuItems.add(new MenuItem(getResources().getString(R.string.nc_join_via_link),
                                       2, ContextCompat.getDrawable(context,
                                                                    R.drawable.ic_link_grey600_24px)));
        }
    }

    @Override
    public boolean onItemClick(View view, int position) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ROOM(), Parcels.wrap(conversation));

        if (menuType.equals(MenuType.REGULAR)) {
            MenuItem menuItem = (MenuItem) adapter.getItem(position);
            if (menuItem != null) {

                int tag = menuItem.getTag();
                if (tag == 5) {
                    conversation.setPassword("");
                }

                if (tag > 0) {
                    if (tag == 1 || tag == 9) {
                        if (tag == 1) {
                            Data data;
                            if ((data = getWorkerData()) != null) {
                                OneTimeWorkRequest leaveConversationWorker =
                                        new OneTimeWorkRequest.Builder(LeaveConversationWorker.class).setInputData(data).build();
                                WorkManager.getInstance().enqueue(leaveConversationWorker);
                            }
                        } else {
                            Bundle deleteConversationBundle;
                            if ((deleteConversationBundle = getDeleteConversationBundle()) != null) {
                                conversationMenuInterface.openLovelyDialogWithIdAndBundle(ConversationsListController.ID_DELETE_CONVERSATION_DIALOG, deleteConversationBundle);
                            }
                        }
                        eventBus.post(new BottomSheetLockEvent(true, 0, false, true));
                    } else {
                        bundle.putInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), tag);
                        if (tag != 2 && tag != 4 && tag != 6 && tag != 7) {
                            eventBus.post(new BottomSheetLockEvent(false, 0, false, false));
                            getRouter().pushController(RouterTransaction.with(new OperationsMenuController(bundle))
                                    .pushChangeHandler(new HorizontalChangeHandler())
                                    .popChangeHandler(new HorizontalChangeHandler()));
                        } else if (tag != 7) {
                            getRouter().pushController(RouterTransaction.with(new EntryMenuController(bundle))
                                    .pushChangeHandler(new HorizontalChangeHandler())
                                    .popChangeHandler(new HorizontalChangeHandler()));
                        } else {
                            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_MENU_TYPE(), Parcels.wrap(MenuType.SHARE));
                            getRouter().pushController(RouterTransaction.with(new CallMenuController(bundle, null))
                                    .pushChangeHandler(new HorizontalChangeHandler())
                                    .popChangeHandler(new HorizontalChangeHandler()));
                        }
                    }
                }
            }
        } else if (menuType.equals(MenuType.SHARE) && position != 0) {
            AppItem appItem = (AppItem) adapter.getItem(position);
            if (appItem != null && getActivity() != null) {
                if (!conversation.hasPassword) {
                    shareIntent.putExtra(Intent.EXTRA_TEXT, ShareUtils.getStringForIntent(getActivity(), null,
                            userUtils, conversation));
                    Intent intent = new Intent(shareIntent);
                    intent.setComponent(new ComponentName(appItem.getPackageName(), appItem.getName()));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    eventBus.post(new BottomSheetLockEvent(true, 0, false, true));
                    getActivity().startActivity(intent);
                } else {
                    bundle.putInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), 7);
                    bundle.putParcelable(BundleKeys.INSTANCE.getKEY_SHARE_INTENT(), Parcels.wrap(shareIntent));
                    bundle.putString(BundleKeys.INSTANCE.getKEY_APP_ITEM_PACKAGE_NAME(), appItem.getPackageName());
                    bundle.putString(BundleKeys.INSTANCE.getKEY_APP_ITEM_NAME(), appItem.getName());
                    getRouter().pushController(RouterTransaction.with(new EntryMenuController(bundle))
                            .pushChangeHandler(new HorizontalChangeHandler())
                            .popChangeHandler(new HorizontalChangeHandler()));
                }
            }
        }

        return true;
    }

    private Data getWorkerData() {
        if (!TextUtils.isEmpty(conversation.getToken())) {
            Data.Builder data = new Data.Builder();
            data.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), conversation.getToken());
            data.putLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), currentUser.getId());
            return data.build();
        }

        return null;
    }

    private Bundle getDeleteConversationBundle() {
        if (!TextUtils.isEmpty(conversation.getToken())) {
            Bundle bundle = new Bundle();
            bundle.putLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), currentUser.getId());
            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ROOM(), Parcels.wrap(conversation));
            return bundle;
        }

        return null;

    }

    @Parcel
    public enum MenuType {
        REGULAR, SHARE
    }
}
