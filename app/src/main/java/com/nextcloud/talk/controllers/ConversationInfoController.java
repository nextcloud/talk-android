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
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.items.UserItem;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.glide.GlideApp;
import com.nextcloud.talk.utils.preferencestorage.DatabaseStorageModule;
import com.yarolegovich.mp.MaterialChoicePreference;
import com.yarolegovich.mp.MaterialPreferenceCategory;
import com.yarolegovich.mp.MaterialPreferenceScreen;

import org.parceler.Parcels;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;


@AutoInjector(NextcloudTalkApplication.class)
public class ConversationInfoController extends BaseController {

    private String baseUrl;
    private String conversationToken;
    private UserEntity conversationUser;
    private String credentials;

    @BindView(R.id.notification_settings)
    MaterialPreferenceScreen materialPreferenceScreen;

    @BindView(R.id.progressBar)
    ProgressBar progressBar;

    @BindView(R.id.conversation_info_message_notifications)
    MaterialChoicePreference messageNotificationLevel;

    @BindView(R.id.conversation_info_name)
    MaterialPreferenceCategory nameCategoryView;

    @BindView(R.id.avatar_image)
    ImageView conversationAvatarImageView;

    @BindView(R.id.display_name_text)
    TextView conversationDisplayName;

    @BindView(R.id.participants_list_category)
    MaterialPreferenceCategory participantsListCategory;

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @Inject
    NcApi ncApi;

    private Disposable roomDisposable;
    private Disposable participantsDisposable;

    private Conversation conversation;

    private FlexibleAdapter<AbstractFlexibleItem> adapter;
    private List<AbstractFlexibleItem> recyclerViewItems = new ArrayList<>();

    public ConversationInfoController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);
        conversationUser = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_USER_ENTITY));
        conversationToken = args.getString(BundleKeys.KEY_ROOM_TOKEN);
        baseUrl = args.getString(BundleKeys.KEY_BASE_URL);
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
            } else {
                recyclerViewItems.add(userItem);
            }
        }


        if (ownUserItem != null) {
            recyclerViewItems.add(0, ownUserItem);
        }

        setupAdapter();

        participantsListCategory.setVisibility(View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_conversation_menu_conversation_info);
    }

    private void getListOfParticipants() {
        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForParticipants(conversationUser.getBaseUrl(), conversationToken))
                .subscribeOn(Schedulers.newThread())
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

    private void fetchRoomInfo() {
        ncApi.getRoom(credentials, ApiUtils.getRoom(conversationUser.getBaseUrl(), conversationToken))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        roomDisposable = d;
                    }

                    @Override
                    public void onNext(RoomOverall roomOverall) {
                        conversation = roomOverall.getOcs().getData();
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

                                messageNotificationLevel.setValue(stringValue);
                            } else {
                                setProperNotificationValue(conversation);
                            }
                        } else {
                            messageNotificationLevel.setEnabled(false);
                            messageNotificationLevel.setAlpha(0.38f);
                            setProperNotificationValue(conversation);
                        }

                        materialPreferenceScreen.setVisibility(View.VISIBLE);
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
        if (conversation.getType().equals(Conversation.RoomType.ROOM_TYPE_ONE_TO_ONE_CALL)) {
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

    private void loadConversationAvatar() {
        int avatarSize = getResources().getDimensionPixelSize(R.dimen.avatar_size_big);

        switch (conversation.getType()) {
            case ROOM_TYPE_ONE_TO_ONE_CALL:
                if (!TextUtils.isEmpty(conversation.getName())) {
                    GlideUrl glideUrl = new GlideUrl(ApiUtils.getUrlForAvatarWithName(conversationUser.getBaseUrl(),
                            conversation.getName(), R.dimen.avatar_size), new LazyHeaders.Builder()
                            .setHeader("Accept", "image/*")
                            .setHeader("User-Agent", ApiUtils.getUserAgent())
                            .build());

                    GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .load(glideUrl)
                            .centerInside()
                            .override(avatarSize, avatarSize)
                            .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                            .into(conversationAvatarImageView);

                }
                break;
            case ROOM_GROUP_CALL:

                GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .load(R.drawable.ic_people_group_white_24px)
                        .centerInside()
                        .override(avatarSize, avatarSize)
                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                        .into(conversationAvatarImageView);
                break;
            case ROOM_PUBLIC_CALL:
                GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .load(R.drawable.ic_link_white_24px)
                        .centerInside()
                        .override(avatarSize, avatarSize)
                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                        .into(conversationAvatarImageView);
                break;
            default:

        }
    }
}
