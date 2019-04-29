/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
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

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.items.UserItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.jobs.DeleteConversationWorker;
import com.nextcloud.talk.jobs.LeaveConversationWorker;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.preferencestorage.DatabaseStorageModule;
import com.vanniktech.emoji.EmojiTextView;
import com.yarolegovich.lovelydialog.LovelySaveStateHandler;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;
import com.yarolegovich.mp.*;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;


@AutoInjector(NextcloudTalkApplication.class)
public class ConversationInfoController extends BaseController {

    private static final int ID_DELETE_CONVERSATION_DIALOG = 0;

    @BindView(R.id.notification_settings)
    MaterialPreferenceScreen materialPreferenceScreen;
    @BindView(R.id.progressBar)
    ProgressBar progressBar;
    @BindView(R.id.conversation_info_message_notifications)
    MaterialChoicePreference messageNotificationLevel;
    @BindView(R.id.conversation_info_name)
    MaterialPreferenceCategory nameCategoryView;
    @BindView(R.id.avatar_image)
    SimpleDraweeView conversationAvatarImageView;
    @BindView(R.id.display_name_text)
    EmojiTextView conversationDisplayName;
    @BindView(R.id.participants_list_category)
    MaterialPreferenceCategory participantsListCategory;
    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;
    @BindView(R.id.deleteConversationAction)
    MaterialStandardPreference deleteConversationAction;
    @BindView(R.id.leaveConversationAction)
    MaterialStandardPreference leaveConversationAction;
    @BindView(R.id.ownOptions)
    MaterialPreferenceCategory ownOptionsCategory;
    @BindView(R.id.muteCalls)
    MaterialSwitchPreference muteCalls;

    @Inject
    NcApi ncApi;
    @Inject
    Context context;

    private String conversationToken;
    private UserEntity conversationUser;
    private String credentials;
    private Disposable roomDisposable;
    private Disposable participantsDisposable;

    private Conversation conversation;

    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private List<AbstractFlexibleItem> recyclerViewItems = new ArrayList<>();

    private LovelySaveStateHandler saveStateHandler;

    public ConversationInfoController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        conversationUser = args.getParcelable(BundleKeys.KEY_USER_ENTITY);
        conversationToken = args.getString(BundleKeys.KEY_ROOM_TOKEN);
        credentials = ApiUtils.getCredentials(conversationUser.getUsername(), conversationUser.getToken());
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
        return inflater.inflate(R.layout.controller_conversation_info, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);

        if (saveStateHandler == null) {
            saveStateHandler = new LovelySaveStateHandler();
        }

        materialPreferenceScreen.setStorageModule(new DatabaseStorageModule(conversationUser, conversationToken));
        if (adapter == null) {
            fetchRoomInfo();
        } else {
            loadConversationAvatar();
            materialPreferenceScreen.setVisibility(View.VISIBLE);
            nameCategoryView.setVisibility(View.VISIBLE);
            participantsListCategory.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            conversationDisplayName.setText(conversation.getDisplayName());
            setupAdapter();
        }
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


    private void showDeleteConversationDialog(Bundle savedInstanceState) {
        if (getActivity() != null) {
            new LovelyStandardDialog(getActivity(), LovelyStandardDialog.ButtonLayout.HORIZONTAL)
                    .setTopColorRes(R.color.nc_darkRed)
                    .setIcon(DisplayUtils.getTintedDrawable(context.getResources(),
                            R.drawable.ic_delete_black_24dp, R.color.white))
                    .setPositiveButtonColor(context.getResources().getColor(R.color.nc_darkRed))
                    .setTitle(R.string.nc_delete_call)
                    .setMessage(conversation.getDeleteWarningMessage())
                    .setPositiveButton(R.string.nc_delete, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            deleteConversation();
                        }
                    })
                    .setNegativeButton(R.string.nc_cancel, null)
                    .setInstanceStateHandler(ID_DELETE_CONVERSATION_DIALOG, saveStateHandler)
                    .setSavedInstanceState(savedInstanceState)
                    .show();
        }
    }

    @Override
    protected void onSaveViewState(@NonNull View view, @NonNull Bundle outState) {
        saveStateHandler.saveInstanceState(outState);
        super.onSaveViewState(view, outState);
    }

    @Override
    protected void onRestoreViewState(@NonNull View view, @NonNull Bundle savedViewState) {
        super.onRestoreViewState(view, savedViewState);
        if (LovelySaveStateHandler.wasDialogOnScreen(savedViewState)) {
            //Dialog won't be restarted automatically, so we need to call this method.
            //Each dialog knows how to restore its state
            showLovelyDialog(LovelySaveStateHandler.getSavedDialogId(savedViewState), savedViewState);
        }
    }

    private void setupAdapter() {
        Activity activity;

        if ((activity = getActivity()) != null) {
            if (adapter == null) {
                adapter = new FlexibleAdapter<>(recyclerViewItems, activity, true);
            }

            if (recyclerView != null) {
                SmoothScrollLinearLayoutManager layoutManager =
                        new SmoothScrollLinearLayoutManager(activity);
                recyclerView.setLayoutManager(layoutManager);
                recyclerView.setHasFixedSize(true);
                recyclerView.setAdapter(adapter);
            }
        }
    }

    private void handleParticipants(List<Participant> participants) {
        UserItem userItem;
        Participant participant;

        recyclerViewItems = new ArrayList<>();
        UserItem ownUserItem = null;

        for (int i = 0; i < participants.size(); i++) {
            participant = participants.get(i);
            userItem = new UserItem(participant, conversationUser, null);
            userItem.setEnabled(!participant.getSessionId().equals("0"));
            if (!TextUtils.isEmpty(participant.getUserId()) && participant.getUserId().equals(conversationUser.getUserId())) {
                ownUserItem = userItem;
                userItem.getModel().setSessionId("-1");
                userItem.setEnabled(true);
            } else {
                recyclerViewItems.add(userItem);
            }
        }


        if (ownUserItem != null) {
            recyclerViewItems.add(0, ownUserItem);
        }

        setupAdapter();

        if (participantsListCategory != null) {
            participantsListCategory.setVisibility(View.VISIBLE);
        }
        adapter.notifyDataSetChanged();
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_conversation_menu_conversation_info);
    }

    private void getListOfParticipants() {
        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForParticipants(conversationUser.getBaseUrl(), conversationToken))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<ParticipantsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        participantsDisposable = d;
                    }

                    @Override
                    public void onNext(ParticipantsOverall participantsOverall) {
                        handleParticipants(participantsOverall.getOcs().getData());
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                        participantsDisposable.dispose();
                    }
                });

    }

    @OnClick(R.id.leaveConversationAction)
    void leaveConversation() {
        Data data;
        if ((data = getWorkerData()) != null) {
            OneTimeWorkRequest leaveConversationWorker =
                    new OneTimeWorkRequest.Builder(LeaveConversationWorker.class).setInputData(data).build();
            WorkManager.getInstance().enqueue(leaveConversationWorker);
            popTwoLastControllers();
        }
    }

    private void deleteConversation() {
        Data data;
        if ((data = getWorkerData()) != null) {
            OneTimeWorkRequest deleteConversationWorker =
                    new OneTimeWorkRequest.Builder(DeleteConversationWorker.class).setInputData(data).build();
            WorkManager.getInstance().enqueue(deleteConversationWorker);
            popTwoLastControllers();
        }
    }

    @OnClick(R.id.deleteConversationAction)
    void deleteConversationClick() {
        showDeleteConversationDialog(null);
    }

    private Data getWorkerData() {
        if (!TextUtils.isEmpty(conversationToken) && conversationUser != null) {
            Data.Builder data = new Data.Builder();
            data.putString(BundleKeys.KEY_ROOM_TOKEN, conversationToken);
            data.putLong(BundleKeys.KEY_INTERNAL_USER_ID, conversationUser.getId());
            return data.build();
        }

        return null;
    }

    private void popTwoLastControllers() {
        List<RouterTransaction> backstack = getRouter().getBackstack();
        backstack = backstack.subList(0, backstack.size() - 2);
        getRouter().setBackstack(backstack, new HorizontalChangeHandler());
    }

    private void fetchRoomInfo() {
        ncApi.getRoom(credentials, ApiUtils.getRoom(conversationUser.getBaseUrl(), conversationToken))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        roomDisposable = d;
                    }

                    @Override
                    public void onNext(RoomOverall roomOverall) {
                        conversation = roomOverall.getOcs().getData();

                        if (isAttached() && (!isBeingDestroyed() || !isDestroyed())) {
                            ownOptionsCategory.setVisibility(View.VISIBLE);

                            if (leaveConversationAction != null) {
                                if (!conversation.canLeave(conversationUser)) {
                                    leaveConversationAction.setVisibility(View.GONE);
                                } else {
                                    leaveConversationAction.setVisibility(View.VISIBLE);
                                }
                            }

                            if (!conversation.canModerate(conversationUser)) {
                                deleteConversationAction.setVisibility(View.GONE);
                            } else {
                                deleteConversationAction.setVisibility(View.VISIBLE);
                            }

                            if (Conversation.ConversationType.ROOM_SYSTEM.equals(conversation.getType())) {
                                muteCalls.setVisibility(View.GONE);
                            }

                            getListOfParticipants();

                            if (progressBar != null) {
                                progressBar.setVisibility(View.GONE);
                            }

                            if (nameCategoryView != null) {
                                nameCategoryView.setVisibility(View.VISIBLE);
                            }

                            if (conversationDisplayName != null) {
                                conversationDisplayName.setText(conversation.getDisplayName());
                            }

                            loadConversationAvatar();

                            if (conversationUser.hasSpreedCapabilityWithName("notification-levels")) {
                                if (messageNotificationLevel != null) {
                                    messageNotificationLevel.setEnabled(true);
                                    messageNotificationLevel.setAlpha(1.0f);
                                }

                                if (!conversation.getNotificationLevel().equals(Conversation.NotificationLevel.DEFAULT)) {
                                    String stringValue;
                                    switch (new EnumNotificationLevelConverter().convertToInt(conversation.getNotificationLevel())) {
                                        case 1:
                                            stringValue = "always";
                                            break;
                                        case 2:
                                            stringValue = "mention";
                                            break;
                                        case 3:
                                            stringValue = "never";
                                            break;
                                        default:
                                            stringValue = "mention";
                                            break;
                                    }

                                    if (messageNotificationLevel != null) {
                                        messageNotificationLevel.setValue(stringValue);
                                    }
                                } else {
                                    setProperNotificationValue(conversation);
                                }
                            } else {
                                if (messageNotificationLevel != null) {
                                    messageNotificationLevel.setEnabled(false);
                                    messageNotificationLevel.setAlpha(0.38f);
                                }
                                setProperNotificationValue(conversation);
                            }

                            materialPreferenceScreen.setVisibility(View.VISIBLE);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                        roomDisposable.dispose();
                    }
                });
    }

    private void setProperNotificationValue(Conversation conversation) {
        if (messageNotificationLevel != null) {
            if (conversation.getType().equals(Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL)) {
                // hack to see if we get mentioned always or just on mention
                if (conversationUser.hasSpreedCapabilityWithName("mention-flag")) {
                    messageNotificationLevel.setValue("always");
                } else {
                    messageNotificationLevel.setValue("mention");
                }
            } else {
                messageNotificationLevel.setValue("mention");
            }
        }
    }

    private void loadConversationAvatar() {
        if (conversationAvatarImageView != null) {
            switch (conversation.getType()) {
                case ROOM_TYPE_ONE_TO_ONE_CALL:
                    if (!TextUtils.isEmpty(conversation.getName())) {
                        DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                                .setOldController(conversationAvatarImageView.getController())
                                .setAutoPlayAnimations(true)
                                .setImageRequest(DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(conversationUser.getBaseUrl(),
                                        conversation.getName(), R.dimen.avatar_size_big), null))
                                .build();
                        conversationAvatarImageView.setController(draweeController);
                    }
                    break;
                case ROOM_GROUP_CALL:
                    conversationAvatarImageView.getHierarchy().setPlaceholderImage(DisplayUtils
                            .getRoundedBitmapDrawableFromVectorDrawableResource(getResources(),
                                    R.drawable.ic_people_group_white_24px));
                    break;
                case ROOM_PUBLIC_CALL:
                    conversationAvatarImageView.getHierarchy().setPlaceholderImage(DisplayUtils
                            .getRoundedBitmapDrawableFromVectorDrawableResource(getResources(),
                                    R.drawable.ic_link_white_24px));
                    break;
                case ROOM_SYSTEM:
                        Drawable[] layers = new Drawable[2];
                        layers[0] = context.getDrawable(R.drawable.ic_launcher_background);
                        layers[1] = context.getDrawable(R.drawable.ic_launcher_foreground);
                        LayerDrawable layerDrawable = new LayerDrawable(layers);
                        conversationAvatarImageView.getHierarchy().setPlaceholderImage(DisplayUtils.getRoundedDrawable(layerDrawable));

                default:
                    break;
            }
        }
    }
}
