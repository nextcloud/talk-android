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

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.nextcloud.talk.R;

import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.converters.EnumNotificationLevelConverter;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.glide.GlideApp;
import com.nextcloud.talk.utils.preferencestorage.DatabaseStorageModule;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.yarolegovich.mp.MaterialChoicePreference;
import com.yarolegovich.mp.MaterialPreferenceCategory;
import com.yarolegovich.mp.MaterialPreferenceScreen;


import org.parceler.Parcels;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import autodagger.AutoInjector;
import butterknife.BindView;
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

    @Inject
    NcApi ncApi;

    private Disposable roomDisposable;
    private Conversation conversation;
    ConversationInfoController(Bundle args) {
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
        if (conversation == null) {
            fetchRoomInfo();
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_conversation_menu_conversation_info);
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

                        progressBar.setVisibility(View.GONE);
                        materialPreferenceScreen.setVisibility(View.VISIBLE);
                        nameCategoryView.setVisibility(View.VISIBLE);
                        conversationDisplayName.setText(conversation.getDisplayName());
                        loadConversationAvatar();

                        if (conversationUser.hasSpreedCapabilityWithName("notification-levels")) {
                            messageNotificationLevel.setEnabled(true);
                            messageNotificationLevel.setAlpha(1.0f);
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
