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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import autodagger.AutoInjector;
import butterknife.BindView;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.ChatController;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.BottomSheetLockEvent;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.call.Call;
import com.nextcloud.talk.models.json.call.CallOverall;
import com.nextcloud.talk.models.json.capabilities.Capabilities;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.participants.AddParticipantOverall;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.singletons.ApplicationWideMessageHolder;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.greenrobot.eventbus.EventBus;
import org.parceler.Parcels;
import retrofit2.HttpException;

import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;

@AutoInjector(NextcloudTalkApplication.class)
public class OperationsMenuController extends BaseController {

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
    private Call call;
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
        this.operationCode = args.getInt(BundleKeys.KEY_OPERATION_CODE);
        if (args.containsKey(BundleKeys.KEY_ROOM)) {
            this.conversation = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ROOM));
        }

        this.callPassword = args.getString(BundleKeys.KEY_CONVERSATION_PASSWORD, "");
        this.callUrl = args.getString(BundleKeys.KEY_CALL_URL, "");

        if (args.containsKey(BundleKeys.KEY_INVITED_PARTICIPANTS)) {
            this.invitedUsers = args.getStringArrayList(BundleKeys.KEY_INVITED_PARTICIPANTS);
        }

        if (args.containsKey(BundleKeys.KEY_INVITED_GROUP)) {
            this.invitedGroups = args.getStringArrayList(BundleKeys.KEY_INVITED_GROUP);
        }

        if (args.containsKey(BundleKeys.KEY_CONVERSATION_TYPE)) {
            this.conversationType = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_CONVERSATION_TYPE));
        }

        if (args.containsKey(BundleKeys.KEY_SERVER_CAPABILITIES)) {
            this.serverCapabilities = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_SERVER_CAPABILITIES));
        }

        this.conversationName = args.getString(BundleKeys.KEY_CONVERSATION_NAME, "");

    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_operations_menu, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        processOperation();
    }

    private void processOperation() {
        currentUser = userUtils.getCurrentUser();
        OperationsObserver operationsObserver = new OperationsObserver();

        if (!TextUtils.isEmpty(callUrl)) {
            conversationToken = callUrl.substring(callUrl.lastIndexOf("/") + 1, callUrl.length());
            if (callUrl.contains("/index.php")) {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/index.php"));
            } else {
                baseUrl = callUrl.substring(0, callUrl.indexOf("/call"));
            }
        }

        if (currentUser != null) {
            credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());

            if (!TextUtils.isEmpty(baseUrl) && !baseUrl.equals(currentUser.getBaseUrl())) {
                credentials = null;
            }

            switch (operationCode) {
                case 2:
                    ncApi.renameRoom(credentials, ApiUtils.getRoom(currentUser.getBaseUrl(), conversation.getToken()),
                            conversation.getName())
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 3:
                    ncApi.makeRoomPublic(credentials, ApiUtils.getUrlForRoomVisibility(currentUser.getBaseUrl(), conversation
                            .getToken()))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 4:
                case 5:
                case 6:
                    String pass = "";
                    if (conversation.getPassword() != null) {
                        pass = conversation.getPassword();
                    }
                    ncApi.setPassword(credentials, ApiUtils.getUrlForPassword(currentUser.getBaseUrl(),
                            conversation.getToken()), pass)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 7:
                    // Operation 7 is sharing, so we handle this differently
                    break;
                case 8:
                    ncApi.makeRoomPrivate(credentials, ApiUtils.getUrlForRoomVisibility(currentUser.getBaseUrl(), conversation
                            .getToken()))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                case 10:
                    ncApi.getRoom(credentials, ApiUtils.getRoom(baseUrl, conversationToken))
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(new Observer<RoomOverall>() {
                                @Override
                                public void onSubscribe(Disposable d) {
                                    disposable = d;
                                }

                                @Override
                                public void onNext(RoomOverall roomOverall) {
                                    conversation = roomOverall.getOcs().getData();
                                    fetchCapabilities(credentials);
                                }

                                @Override
                                public void onError(Throwable e) {
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
                    boolean isGroupCallWorkaround = false;
                    String invite = null;

                    if (invitedGroups.size() > 0) {
                        invite = invitedGroups.get(0);
                    }

                    if (conversationType.equals(Conversation.ConversationType.ROOM_PUBLIC_CALL) ||
                            !currentUser.hasSpreedCapabilityWithName("empty-group-room")) {
                        retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(currentUser.getBaseUrl(),
                                "3", invite, conversationName);
                    } else {
                        String roomType = "2";
                        if (!currentUser.hasSpreedCapabilityWithName("empty-group-room")) {
                            isGroupCallWorkaround = true;
                            roomType = "3";
                        }

                        retrofitBucket = ApiUtils.getRetrofitBucketForCreateRoom(currentUser.getBaseUrl(),
                                roomType, invite, conversationName);
                    }

                    final boolean isGroupCallWorkaroundFinal = isGroupCallWorkaround;
                    ncApi.createRoom(credentials, retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(new Observer<RoomOverall>() {
                                @Override
                                public void onSubscribe(Disposable d) {

                                }

                                @Override
                                public void onNext(RoomOverall roomOverall) {
                                    conversation = roomOverall.getOcs().getData();
                                    if (conversationType.equals(Conversation.ConversationType.ROOM_PUBLIC_CALL) && isGroupCallWorkaroundFinal) {
                                        performGroupCallWorkaround(credentials);
                                    } else {
                                        inviteUsersToAConversation();
                                    }
                                }

                                @Override
                                public void onError(Throwable e) {
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
                        ncApi.removeConversationFromFavorites(credentials, ApiUtils.getUrlForConversationFavorites(currentUser.getBaseUrl(),
                                conversation.getToken()))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .retry(1)
                                .subscribe(operationsObserver);
                    } else {
                        ncApi.addConversationToFavorites(credentials, ApiUtils.getUrlForConversationFavorites(currentUser.getBaseUrl(),
                                conversation.getToken()))
                                .subscribeOn(Schedulers.io())
                                .observeOn(AndroidSchedulers.mainThread())
                                .retry(1)
                                .subscribe(operationsObserver);
                    }
                    break;
                case 99:
                    ncApi.joinRoom(credentials, ApiUtils.getUrlForSettingMyselfAsActiveParticipant(baseUrl, conversationToken),
                            callPassword)
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(operationsObserver);
                    break;
                default:
                    break;
            }
        }
    }

    private void performGroupCallWorkaround(String credentials) {
        ncApi.makeRoomPrivate(credentials, ApiUtils.getUrlForRoomVisibility(currentUser.getBaseUrl(), conversation.getToken()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .retry(1)
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        inviteUsersToAConversation();
                    }

                    @Override
                    public void onError(Throwable e) {
                        showResultImage(false, false);
                        dispose();
                    }

                    @Override
                    public void onComplete() {
                        dispose();
                    }
                });


    }

    private void showResultImage(boolean everythingOK, boolean isGuestSupportError) {
        progressBar.setVisibility(View.GONE);

        if (everythingOK) {
            resultImageView.setImageDrawable(DisplayUtils.getTintedDrawable(getResources(), R.drawable
                    .ic_check_circle_black_24dp, R.color.nc_darkGreen));
        } else {
            resultImageView.setImageDrawable(DisplayUtils.getTintedDrawable(getResources(), R.drawable
                    .ic_cancel_black_24dp, R.color.nc_darkRed));
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

    private void fetchCapabilities(String credentials) {
        ncApi.getCapabilities(credentials, ApiUtils.getUrlForCapabilities(baseUrl))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<CapabilitiesOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(CapabilitiesOverall capabilitiesOverall) {
                        if (capabilitiesOverall.getOcs().getData()
                                .getCapabilities().getSpreedCapability() != null &&
                                capabilitiesOverall.getOcs().getData()
                                        .getCapabilities().getSpreedCapability()
                                        .getFeatures() != null && capabilitiesOverall.getOcs().getData()
                                .getCapabilities().getSpreedCapability()
                                .getFeatures().contains("chat-v2")) {
                            if (conversation.isHasPassword() && conversation.isGuest()) {
                                eventBus.post(new BottomSheetLockEvent(true, 0,
                                        true, false));
                                Bundle bundle = new Bundle();
                                bundle.putParcelable(BundleKeys.KEY_ROOM, Parcels.wrap(conversation));
                                bundle.putString(BundleKeys.KEY_CALL_URL, callUrl);
                                bundle.putParcelable(BundleKeys.KEY_SERVER_CAPABILITIES,
                                        Parcels.wrap(capabilitiesOverall.getOcs().getData().getCapabilities()));
                                bundle.putInt(BundleKeys.KEY_OPERATION_CODE, 99);
                                getRouter().pushController(RouterTransaction.with(new EntryMenuController(bundle))
                                        .pushChangeHandler(new HorizontalChangeHandler())
                                        .popChangeHandler(new HorizontalChangeHandler()));
                            } else {
                                initiateConversation(false, capabilitiesOverall.getOcs().getData()
                                        .getCapabilities());
                            }
                        } else if (capabilitiesOverall.getOcs().getData()
                                .getCapabilities().getSpreedCapability() != null &&
                                capabilitiesOverall.getOcs().getData()
                                        .getCapabilities().getSpreedCapability()
                                        .getFeatures() != null && capabilitiesOverall.getOcs().getData()
                                .getCapabilities().getSpreedCapability()
                                .getFeatures().contains("guest-signaling")) {
                            initiateCall();
                        } else {
                            showResultImage(false, true);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        showResultImage(false, false);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void inviteUsersToAConversation() {
        RetrofitBucket retrofitBucket;
        final ArrayList<String> localInvitedUsers = invitedUsers;
        final ArrayList<String> localInvitedGroups = invitedGroups;
        if (localInvitedGroups.size() > 0) {
            localInvitedGroups.remove(0);
        }

        if (localInvitedUsers.size() > 0 || (localInvitedGroups.size() > 0 && currentUser.hasSpreedCapabilityWithName("invite-groups-and-mails"))) {
            if ((localInvitedGroups.size() > 0 && currentUser.hasSpreedCapabilityWithName("invite-groups-and-mails"))) {
                for (int i = 0; i < localInvitedGroups.size(); i++) {
                    final String groupId = localInvitedGroups.get(i);
                    retrofitBucket = ApiUtils.getRetrofitBucketForAddGroupParticipant(currentUser.getBaseUrl(), conversation.getToken(),
                            groupId);

                    ncApi.addParticipant(credentials, retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                            .subscribeOn(Schedulers.io())
                            .observeOn(AndroidSchedulers.mainThread())
                            .retry(1)
                            .subscribe(new Observer<AddParticipantOverall>() {
                                @Override
                                public void onSubscribe(Disposable d) {

                                }

                                @Override
                                public void onNext(AddParticipantOverall addParticipantOverall) {
                                }

                                @Override
                                public void onError(Throwable e) {
                                    dispose();
                                }

                                @Override
                                public void onComplete() {
                                    synchronized (localInvitedGroups) {
                                        localInvitedGroups.remove(groupId);
                                    }

                                    if (localInvitedGroups.size() == 0 && localInvitedUsers.size() == 0) {
                                        initiateConversation(true, null);
                                    }
                                    dispose();
                                }
                            });

                }
            }

            for (int i = 0; i < localInvitedUsers.size(); i++) {
                final String userId = invitedUsers.get(i);
                retrofitBucket = ApiUtils.getRetrofitBucketForAddParticipant(currentUser.getBaseUrl(), conversation.getToken(),
                        userId);

                ncApi.addParticipant(credentials, retrofitBucket.getUrl(), retrofitBucket.getQueryMap())
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(1)
                        .subscribe(new Observer<AddParticipantOverall>() {
                            @Override
                            public void onSubscribe(Disposable d) {

                            }

                            @Override
                            public void onNext(AddParticipantOverall addParticipantOverall) {
                            }

                            @Override
                            public void onError(Throwable e) {
                                dispose();
                            }

                            @Override
                            public void onComplete() {
                                synchronized (localInvitedUsers) {
                                    localInvitedUsers.remove(userId);
                                }

                                if (localInvitedGroups.size() == 0 && localInvitedUsers.size() == 0) {
                                    initiateConversation(true, null);
                                }
                                dispose();
                            }
                        });
            }
        } else {
            if (!currentUser.hasSpreedCapabilityWithName("chat-v2")) {
                showResultImage(true, false);
            } else {
                initiateConversation(true, null);
            }
        }
    }

    private void initiateConversation(boolean dismissView, @Nullable Capabilities capabilities) {
        Bundle bundle = new Bundle();
        boolean isGuestUser = false;
        boolean hasChatCapability;

        if (baseUrl != null && !baseUrl.equals(currentUser.getBaseUrl())) {
            isGuestUser = true;
            hasChatCapability = capabilities != null && capabilities.getSpreedCapability() != null && capabilities.getSpreedCapability().getFeatures() != null && capabilities.getSpreedCapability().getFeatures().contains("chat-v2");
        } else {
            hasChatCapability = currentUser.hasSpreedCapabilityWithName("chat-v2");
        }


        if (hasChatCapability) {
            eventBus.post(new BottomSheetLockEvent(true, 0,
                    true, true, dismissView));

            Intent conversationIntent = new Intent(getActivity(), MagicCallActivity.class);
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation.getToken());
            bundle.putString(BundleKeys.KEY_ROOM_ID, conversation.getRoomId());
            bundle.putString(BundleKeys.KEY_CONVERSATION_NAME, conversation.getDisplayName());
            UserEntity conversationUser;
            if (isGuestUser) {
                conversationUser = new UserEntity();
                conversationUser.setBaseUrl(baseUrl);
                conversationUser.setUserId("?");
                try {
                    conversationUser.setCapabilities(LoganSquare.serialize(capabilities));
                } catch (IOException e) {
                    Log.e("OperationsMenu", "Failed to serialize capabilities");
                }
            } else {
                conversationUser = currentUser;
            }

            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, conversationUser);
            bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION, Parcels.wrap(conversation));
            bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD, callPassword);

            conversationIntent.putExtras(bundle);

            if (getParentController() != null) {
                getParentController().getRouter().replaceTopController(RouterTransaction.with(new ChatController(bundle))
                        .pushChangeHandler(new HorizontalChangeHandler())
                        .popChangeHandler(new HorizontalChangeHandler()));
            }
        } else {
            initiateCall();
        }
    }


    private void initiateCall() {
        eventBus.post(new BottomSheetLockEvent(true, 0, true, true));
        Bundle bundle = new Bundle();
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, conversation.getToken());
        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, currentUser);
        if (baseUrl != null && !baseUrl.equals(currentUser.getBaseUrl())) {
            bundle.putString(BundleKeys.KEY_MODIFIED_BASE_URL, baseUrl);
        }
        bundle.putParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION, Parcels.wrap(call));

        if (getActivity() != null) {

            Intent callIntent = new Intent(getActivity(), MagicCallActivity.class);
            callIntent.putExtras(bundle);

            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Activity.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.toggleSoftInput(InputMethodManager.HIDE_IMPLICIT_ONLY, 0);
            }

            new Handler().postDelayed(() -> getParentController().getRouter().popCurrentController(), 100);
            startActivity(callIntent);

        }

    }

    private class OperationsObserver implements Observer {

        @Override
        public void onSubscribe(Disposable d) {
            disposable = d;
        }

        @Override
        public void onNext(Object o) {
            if (operationCode != 99) {
                showResultImage(true, false);
            } else {
                CallOverall callOverall = (CallOverall) o;
                call = callOverall.getOcs().getData();
                initiateConversation(true, serverCapabilities);
            }
        }

        @Override
        public void onError(Throwable e) {
            if (operationCode != 99 || !(e instanceof HttpException)) {
                showResultImage(false, false);
            } else {
                if (((HttpException) e).response().code() == 403) {
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

        @Override
        public void onComplete() {
            dispose();
        }
    }
}
