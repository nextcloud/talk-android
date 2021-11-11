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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.models.database.CapabilitiesUtil;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.capabilities.Capabilities;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.participants.AddParticipantOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.ConductorRemapping;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.NoSupportedApiException;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder;

import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcels;

import java.io.IOException;
import java.util.ArrayList;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import autodagger.AutoInjector;
import butterknife.BindView;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import retrofit2.HttpException;
import retrofit2.Response;

@AutoInjector(NextcloudTalkApplication.class)
public class OperationsMenuController extends BaseController {

    private static final String TAG = "OperationsMenuController";

    @BindView(R.id.progress_bar)
    ProgressBar progressBar;

    @BindView(R.id.result_image_view)
    ImageView resultImageView;

    @BindView(R.id.result_text_view)
    TextView resultsTextView;

    @BindView(R.id.ok_button)
    Button okButton;

    @BindView(R.id.web_button)
    Button webButton;

    @Inject
    NcApi ncApi;

    @Inject
    UserUtils userUtils;

    @Inject
    EventBus eventBus;

    private int operationCode;
    private Conversation conversation;

    private UserEntity currentUser;
    private String callPassword;
    private String callUrl;

    private String baseUrl;
    private String conversationToken;

    private Disposable disposable;

    private Conversation.ConversationType conversationType;
    private ArrayList<String> invitedUsers = new ArrayList<>();
    private ArrayList<String> invitedGroups = new ArrayList<>();

    private Capabilities serverCapabilities;
    private String credentials;
    private String conversationName;

    public OperationsMenuController(Bundle args) {
        super(args);
        this.operationCode = args.getInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE());
        if (args.containsKey(BundleKeys.INSTANCE.getKEY_ROOM())) {
            this.conversation = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_ROOM()));
        }

        this.callPassword = args.getString(BundleKeys.INSTANCE.getKEY_CONVERSATION_PASSWORD(), "");
        this.callUrl = args.getString(BundleKeys.INSTANCE.getKEY_CALL_URL(), "");

        if (args.containsKey(BundleKeys.INSTANCE.getKEY_INVITED_PARTICIPANTS())) {
            this.invitedUsers = args.getStringArrayList(BundleKeys.INSTANCE.getKEY_INVITED_PARTICIPANTS());
        }

        if (args.containsKey(BundleKeys.INSTANCE.getKEY_INVITED_GROUP())) {
            this.invitedGroups = args.getStringArrayList(BundleKeys.INSTANCE.getKEY_INVITED_GROUP());
        }

        if (args.containsKey(BundleKeys.INSTANCE.getKEY_CONVERSATION_TYPE())) {
            this.conversationType = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_CONVERSATION_TYPE()));
        }

        if (args.containsKey(BundleKeys.INSTANCE.getKEY_SERVER_CAPABILITIES())) {
            this.serverCapabilities = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_SERVER_CAPABILITIES()));
        }

        this.conversationName = args.getString(BundleKeys.INSTANCE.getKEY_CONVERSATION_NAME(), "");

    }

    @NonNull
    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_operations_menu, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        currentUser = userUtils.getCurrentUser();
        if (!TextUtils.isEmpty(callUrl) && callUrl.contains("/call")) {
            conversationToken = callUrl.substring(callUrl.lastIndexOf("/") + 1);
            if (callUrl.contains("/index.php")) {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/index.php"));
            } else {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/call"));
            }
        }

        if (!TextUtils.isEmpty(baseUrl) && !baseUrl.equals(currentUser.getBaseUrl())) {
            if (serverCapabilities != null) {
                try {
                    useBundledCapabilitiesForGuest();
                } catch (IOException e) {
                    // Fall back to fetching capabilities again
                    fetchCapabilitiesForGuest();
                }
            } else {
                fetchCapabilitiesForGuest();
            }
        } else {
            processOperation();
        }
    }


    @SuppressLint("LongLogTag")
    private void useBundledCapabilitiesForGuest() throws IOException {
        currentUser = new UserEntity();
        currentUser.setBaseUrl(baseUrl);
        currentUser.setUserId("?");
        try {
            currentUser.setCapabilities(LoganSquare.serialize(serverCapabilities));
        } catch (IOException e) {
            Log.e("OperationsMenu", "Failed to serialize capabilities");
            throw e;
        }

        try {
            checkCapabilities(currentUser);
            processOperation();
        } catch (NoSupportedApiException e) {
            showResultImage(false, false);
            Log.d(TAG, "No supported server version found", e);
        }
    }

    @SuppressLint("LongLogTag")
    private void fetchCapabilitiesForGuest() {
        ncApi.getCapabilities(null, ApiUtils.getUrlForCapabilities(baseUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<CapabilitiesOverall>() {
                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                    }

                    @SuppressLint("LongLogTag")
                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull CapabilitiesOverall capabilitiesOverall) {
                        currentUser = new UserEntity();
                        currentUser.setBaseUrl(baseUrl);
                        currentUser.setUserId("?");
                        try {
                            currentUser.setCapabilities(
                                    LoganSquare
                                            .serialize(
                                                    capabilitiesOverall
                                                            .getOcs()
                                                            .getData()
                                                            .getCapabilities()));
                        } catch (IOException e) {
                            Log.e("OperationsMenu", "Failed to serialize capabilities");
                        }

                        try {
                            checkCapabilities(currentUser);
                            processOperation();
                        } catch (NoSupportedApiException e) {
                            showResultImage(false, false);
                            Log.d(TAG, "No supported server version found", e);
                        }
                    }

                    @SuppressLint("LongLogTag")
                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        showResultImage(false, false);
                        Log.e(TAG, "Error fetching capabilities for guest", e);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    @SuppressLint("LongLogTag")
    private void processOperation() {
        RoomOperationsObserver roomOperationsObserver = new RoomOperationsObserver();
        GenericOperationsObserver genericOperationsObserver = new GenericOperationsObserver();

        if (currentUser == null) {
            showResultImage(false, true);
            Log.e(TAG, "Ended up in processOperation without a valid currentUser");
            return;
        }

        credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());
        int apiVersion = ApiUtils.getConversationApiVersion(currentUser, new int[] {ApiUtils.APIv4, 1});

        switch (operationCode) {
            case 2:
                ncApi.renameRoom(credentials, ApiUtils.getUrlForRoom(apiVersion, currentUser.getBaseUrl(),
                                                                     conversation.getToken()),
                        conversation.getName())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(genericOperationsObserver);
                break;
            case 3:
                ncApi.makeRoomPublic(credentials, ApiUtils.getUrlForRoomPublic(apiVersion, currentUser.getBaseUrl(),
                                                                               conversation.getToken()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(genericOperationsObserver);
                break;
            case 4:
            case 5:
            case 6:
                String pass = "";
                if (conversation.getPassword() != null) {
                    pass = conversation.getPassword();
                }
                ncApi.setPassword(credentials, ApiUtils.getUrlForRoomPassword(apiVersion, currentUser.getBaseUrl(),
                                                                              conversation.getToken()), pass)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(genericOperationsObserver);
                break;
            case 7:
                // Operation 7 is sharing, so we handle this differently
                break;
            case 8:
                ncApi.makeRoomPrivate(credentials, ApiUtils.getUrlForRoomPublic(apiVersion,
                                                                                currentUser.getBaseUrl(),
                                                                                conversation.getToken()))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(genericOperationsObserver);
                break;
            case 10:
                ncApi.getRoom(credentials, ApiUtils.getUrlForRoom(apiVersion, baseUrl, conversationToken))
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(new Observer<RoomOverall>() {
                            @Override
                            public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                                disposable = d;
                            }

                            @Override
                            public void onNext(@io.reactivex.annotations.NonNull RoomOverall roomOverall) {
                                conversation = roomOverall.getOcs().getData();
                                if (conversation.isHasPassword() && conversation.isGuest()) {
                                    eventBus.post(new BottomSheetLockEvent(true, 0,
                                                                           true, false));
                                    Bundle bundle = new Bundle();
                                    bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ROOM(), Parcels.wrap(conversation));
                                    bundle.putString(BundleKeys.INSTANCE.getKEY_CALL_URL(), callUrl);
                                    try {
                                        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_SERVER_CAPABILITIES(),
                                                             Parcels.wrap(LoganSquare.parse(currentUser.getCapabilities(),
                                                                                            Capabilities.class)));
                                    } catch (IOException e) {
                                        Log.e(TAG, "Failed to parse capabilities for guest");
                                        showResultImage(false, false);
                                    }
                                    bundle.putInt(BundleKeys.INSTANCE.getKEY_OPERATION_CODE(), 99);
                                    getRouter().pushController(RouterTransaction.with(new EntryMenuController(bundle))
                                                                       .pushChangeHandler(new HorizontalChangeHandler())
                                                                       .popChangeHandler(new HorizontalChangeHandler()));
                                } else if (conversation.isGuest()) {
                                    ncApi.joinRoom(credentials, ApiUtils.getUrlForParticipantsActive(apiVersion,
                                                                                                     baseUrl,
                                                                                                     conversationToken), null)
                                            .subscribeOn(Schedulers.io())
                                            .observeOn(AndroidSchedulers.mainThread())
                                            .subscribe(new Observer<RoomOverall>() {
                                                @Override
                                                public void onSubscribe(
                                                        @io.reactivex.annotations.NonNull Disposable d
                                                                       ) {
                                                }

                                                @Override
                                                public void onNext(
                                                        @io.reactivex.annotations.NonNull RoomOverall roomOverall
                                                                  ) {
                                                    conversation = roomOverall.getOcs().getData();
                                                    initiateConversation(false);
                                                }

                                                @Override
                                                public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                                    showResultImage(false, false);
                                                    dispose();
                                                }

                                                @Override
                                                public void onComplete() {
                                                }
                                            });
                                } else {
                                    initiateConversation(false);
                                }
                            }

                            @Override
                            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                showResultImage(false, false);
                                dispose();
                            }

                            @Override
                            public void onComplete() {
                                dispose();
                            }
                        });
                break;
            case 11:
                RetrofitBucket retrofitBucket;
                String invite = null;

                if (invitedGroups.size() > 0) {
                    invite = invitedGroups.get(0);
                }

                if (conversationType.equals(Conversation.ConversationType.ROOM_PUBLIC_CALL)) {
                    retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(apiVersion, currentUser.getBaseUrl(),
                                                                             "3", null, invite, conversationName);
                } else {
                    retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(apiVersion, currentUser.getBaseUrl(),
                                                                             "2", null, invite, conversationName);
                }

                ncApi.createRoom(credentials, retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(new Observer<RoomOverall>() {
                            @Override
                            public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {

                            }

                            @Override
                            public void onNext(@io.reactivex.annotations.NonNull RoomOverall roomOverall) {
                                conversation = roomOverall.getOcs().getData();

                                ncApi.getRoom(credentials,
                                        ApiUtils.getUrlForRoom(apiVersion, currentUser.getBaseUrl(),
                                                         conversation.getToken()))
                                        .subscribeOn(Schedulers.io())
                                        .observeOn(AndroidSchedulers.mainThread())
                                        .subscribe(new Observer<RoomOverall>() {
                                            @Override
                                            public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {

                                            }

                                            @Override
                                            public void onNext(
                                                    @io.reactivex.annotations.NonNull RoomOverall roomOverall
                                                              ) {
                                                conversation = roomOverall.getOcs().getData();
                                                inviteUsersToAConversation();
                                            }

                                            @Override
                                            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                                showResultImage(false, false);
                                                dispose();
                                            }

                                            @Override
                                            public void onComplete() {

                                            }
                                        });

                            }

                            @Override
                            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                showResultImage(false, false);
                                dispose();
                            }

                            @Override
                            public void onComplete() {
                                dispose();
                            }
                        });

                break;
            case 97:
            case 98:
                if (operationCode == 97) {
                    ncApi.removeConversationFromFavorites(credentials,
                                                          ApiUtils.getUrlForRoomFavorite(apiVersion,
                                                                                         currentUser.getBaseUrl(),
                                                                                         conversation.getToken()))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(genericOperationsObserver);
                } else {
                    ncApi.addConversationToFavorites(credentials,
                                                     ApiUtils.getUrlForRoomFavorite(apiVersion,
                                                                                    currentUser.getBaseUrl(),
                                                                                    conversation.getToken()))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(genericOperationsObserver);
                }
                break;
            case 99:
                ncApi.joinRoom(credentials, ApiUtils.getUrlForParticipantsActive(apiVersion,
                                                                                 baseUrl,
                                                                                 conversationToken),
                        callPassword)
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(roomOperationsObserver);
                break;
            default:
                break;
        }
    }

    private void showResultImage(boolean everythingOK, boolean isGuestSupportError) {
        progressBar.setVisibility(View.GONE);

        if (getResources() != null) {
            if (everythingOK) {
                resultImageView.setImageDrawable(DisplayUtils.getTintedDrawable(getResources(),
                                                                                R.drawable.ic_check_circle_black_24dp,
                                                                                R.color.nc_darkGreen));
            } else {
                resultImageView.setImageDrawable(DisplayUtils.getTintedDrawable(getResources(),
                                                                                R.drawable.ic_cancel_black_24dp,
                                                                                R.color.nc_darkRed));
            }
        }

        resultImageView.setVisibility(View.VISIBLE);

        if (everythingOK) {
            resultsTextView.setText(R.string.nc_all_ok_operation);
        } else {
            resultsTextView.setTextColor(getResources().getColor(R.color.nc_darkRed));
            if (!isGuestSupportError) {
                resultsTextView.setText(R.string.nc_failed_to_perform_operation);
            } else {
                resultsTextView.setText(R.string.nc_failed_signaling_settings);
                webButton.setOnClickListener(v -> {
                    eventBus.post(new BottomSheetLockEvent(true, 0, false, true));
                    Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(callUrl));
                    startActivity(browserIntent);
                    new BottomSheetLockEvent(true, 0, false, true);
                });
                webButton.setVisibility(View.VISIBLE);
            }
        }

        resultsTextView.setVisibility(View.VISIBLE);
        if (everythingOK) {
            eventBus.post(new BottomSheetLockEvent(true, 2500, true, true));
        } else {
            resultImageView.setImageDrawable(DisplayUtils.getTintedDrawable(getResources(), R.drawable
                    .ic_cancel_black_24dp, R.color.nc_darkRed));
            okButton.setOnClickListener(v -> eventBus.post(new BottomSheetLockEvent(true, 0, operationCode != 99
                    && operationCode != 10, true)));
            okButton.setVisibility(View.VISIBLE);
        }
    }

    private void dispose() {
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }

        disposable = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        dispose();
    }

    private void checkCapabilities(UserEntity currentUser) throws NoSupportedApiException {
        ApiUtils.getConversationApiVersion(currentUser, new int[] {ApiUtils.APIv4, 1});
        ApiUtils.getCallApiVersion(currentUser, new int[] {ApiUtils.APIv4, 1});
        ApiUtils.getChatApiVersion(currentUser, new int[] {1});
        ApiUtils.getSignalingApiVersion(currentUser, new int[] {ApiUtils.APIv3, 2, 1});
    }

    private void inviteUsersToAConversation() {
        RetrofitBucket retrofitBucket;
        final ArrayList<String> localInvitedUsers = invitedUsers;
        final ArrayList<String> localInvitedGroups = invitedGroups;
        if (localInvitedGroups.size() > 0) {
            localInvitedGroups.remove(0);
        }

        int apiVersion = ApiUtils.getConversationApiVersion(currentUser, new int[] {4, 1});

        if (localInvitedUsers.size() > 0 || (localInvitedGroups.size() > 0 &&
                CapabilitiesUtil.hasSpreedFeatureCapability(currentUser, "invite-groups-and-mails"))) {
            addGroupsToConversation(localInvitedUsers, localInvitedGroups, apiVersion);
            addUsersToConversation(localInvitedUsers, localInvitedGroups, apiVersion);
        } else {
            initiateConversation(true);
        }
    }

    private void addUsersToConversation(
            ArrayList<String> localInvitedUsers,
            ArrayList<String> localInvitedGroups,
            int apiVersion)
    {
        RetrofitBucket retrofitBucket;
        for (int i = 0; i < localInvitedUsers.size(); i++) {
            final String userId = invitedUsers.get(i);
            retrofitBucket = ApiUtils.getRetrofitBucketForAddParticipant(apiVersion,
                                                                         currentUser.getBaseUrl(),
                                                                         conversation.getToken(),
                                                                         userId);

            ncApi.addParticipant(credentials, retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .retry(1)
                    .subscribe(new Observer<AddParticipantOverall>() {
                        @Override
                        public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {

                        }

                        @Override
                        public void onNext(
                                @io.reactivex.annotations.NonNull AddParticipantOverall addParticipantOverall
                                          ) {
                        }

                        @Override
                        public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                            dispose();
                        }

                        @Override
                        public void onComplete() {
                            synchronized (localInvitedUsers) {
                                localInvitedUsers.remove(userId);
                            }

                            if (localInvitedGroups.size() == 0 && localInvitedUsers.size() == 0) {
                                initiateConversation(true);
                            }
                            dispose();
                        }
                    });
        }
    }

    private void addGroupsToConversation(
            ArrayList<String> localInvitedUsers,
            ArrayList<String> localInvitedGroups,
            int apiVersion)
    {
        RetrofitBucket retrofitBucket;
        if ((localInvitedGroups.size() > 0 &&
                CapabilitiesUtil.hasSpreedFeatureCapability(currentUser, "invite-groups-and-mails"))) {
            for (int i = 0; i < localInvitedGroups.size(); i++) {
                final String groupId = localInvitedGroups.get(i);
                retrofitBucket = ApiUtils.getRetrofitBucketForAddParticipantWithSource(
                        apiVersion,
                        currentUser.getBaseUrl(),
                        conversation.getToken(),
                        "groups",
                        groupId
                                                                                      );

                ncApi.addParticipant(credentials, retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(new Observer<AddParticipantOverall>() {
                            @Override
                            public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {

                            }

                            @Override
                            public void onNext(
                                    @io.reactivex.annotations.NonNull AddParticipantOverall addParticipantOverall
                                              ) {
                            }

                            @Override
                            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                dispose();
                            }

                            @Override
                            public void onComplete() {
                                synchronized (localInvitedGroups) {
                                    localInvitedGroups.remove(groupId);
                                }

                                if (localInvitedGroups.size() == 0 && localInvitedUsers.size() == 0) {
                                    initiateConversation(true);
                                }
                                dispose();
                            }
                        });

            }
        }
    }

    private void initiateConversation(boolean dismissView) {
        eventBus.post(new BottomSheetLockEvent(true, 0,
                                               true, true, dismissView));

        Bundle bundle = new Bundle();
        bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), conversation.getToken());
        bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), conversation.getRoomId());
        bundle.putString(BundleKeys.INSTANCE.getKEY_CONVERSATION_NAME(), conversation.getDisplayName());
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), currentUser);
        bundle.putParcelable(BundleKeys.INSTANCE.getKEY_ACTIVE_CONVERSATION(), Parcels.wrap(conversation));
        bundle.putString(BundleKeys.INSTANCE.getKEY_CONVERSATION_PASSWORD(), callPassword);

        if (getParentController() != null) {
            ConductorRemapping.INSTANCE.remapChatController(getParentController().getRouter(), currentUser.getId(),
                                                            conversation.getToken(), bundle, true);
        }
    }

    private void handleObserverError(@io.reactivex.annotations.NonNull Throwable e) {
        if (operationCode != 99 || !(e instanceof HttpException)) {
            showResultImage(false, false);
        } else {
            Response<?> response = ((HttpException) e).response();
            if (response != null && response.code() == 403) {
                eventBus.post(new BottomSheetLockEvent(true, 0, false,
                                                       false));
                ApplicationWideMessageHolder.getInstance().setMessageType(ApplicationWideMessageHolder.MessageType.CALL_PASSWORD_WRONG);
                getRouter().popCurrentController();
            } else {
                showResultImage(false, false);
            }
        }
        dispose();
    }

    private class GenericOperationsObserver implements Observer<GenericOverall> {

        @Override
        public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
            disposable = d;
        }

        @Override
        public void onNext(@io.reactivex.annotations.NonNull GenericOverall genericOverall) {
            if (operationCode != 99) {
                showResultImage(true, false);
            } else {
                throw new IllegalArgumentException("Unsupported operation code observed!");
            }
        }

        @Override
        public void onError(@io.reactivex.annotations.NonNull Throwable e) {
            handleObserverError(e);
        }

        @Override
        public void onComplete() {
            dispose();
        }
    }

    private class RoomOperationsObserver implements Observer<RoomOverall> {

        @Override
        public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
            disposable = d;
        }

        @Override
        public void onNext(@io.reactivex.annotations.NonNull RoomOverall roomOverall) {
            conversation = roomOverall.getOcs().getData();
            if (operationCode != 99) {
                showResultImage(true, false);
            } else {
                conversation = roomOverall.getOcs().getData();
                initiateConversation(true);
            }
        }

        @Override
        public void onError(@io.reactivex.annotations.NonNull Throwable e) {
            handleObserverError(e);
        }

        @Override
        public void onComplete() {
            dispose();
        }
    }
}
