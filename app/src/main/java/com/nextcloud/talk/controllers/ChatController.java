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


import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.amulyakhare.textdrawable.TextDrawable;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.adapters.messages.MagicIncomingTextMessageViewHolder;
import com.nextcloud.talk.adapters.messages.MagicOutcomingTextMessageViewHolder;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.callbacks.MentionAutocompleteCallback;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.call.Call;
import com.nextcloud.talk.models.json.call.CallOverall;
import com.nextcloud.talk.models.json.chat.ChatMessage;
import com.nextcloud.talk.models.json.chat.ChatOverall;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.mention.Mention;
import com.nextcloud.talk.models.json.rooms.Room;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.models.json.rooms.RoomsOverall;
import com.nextcloud.talk.presenters.MentionAutocompletePresenter;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.KeyboardUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.glide.GlideApp;
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder;
import com.otaliastudios.autocomplete.Autocomplete;
import com.otaliastudios.autocomplete.AutocompleteCallback;
import com.otaliastudios.autocomplete.AutocompletePresenter;
import com.otaliastudios.autocomplete.CharPolicy;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessageHolders;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.stfalcon.chatkit.utils.DateFormatter;
import com.webianks.library.PopupBubble;

import org.parceler.Parcels;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;
import retrofit2.HttpException;
import retrofit2.Response;

@AutoInjector(NextcloudTalkApplication.class)
public class ChatController extends BaseController implements MessagesListAdapter.OnLoadMoreListener,
        MessagesListAdapter.Formatter<Date>, MessagesListAdapter.OnMessageLongClickListener {
    private static final String TAG = "ChatController";

    @Inject
    NcApi ncApi;
    @Inject
    UserUtils userUtils;
    @Inject
    Cache cache;

    @BindView(R.id.messagesListView)
    MessagesList messagesListView;
    @BindView(R.id.messageInputView)
    MessageInput messageInputView;
    @BindView(R.id.popupBubbleView)
    PopupBubble popupBubble;
    @BindView(R.id.emptyLayout)
    RelativeLayout emptyLayout;
    @BindView(R.id.sendHiTextView)
    TextView sendHiTextView;
    @BindView(R.id.progressBar)
    ProgressBar loadingProgressBar;
    private List<Disposable> disposableList = new ArrayList<>();
    private String conversationName;
    private String roomToken;
    private UserEntity conversationUser;
    private String roomPassword;
    private String credentials;
    private String baseUrl;
    private Call currentCall;
    private boolean inChat = false;
    private boolean historyRead = false;
    private int globalLastKnownFutureMessageId = -1;
    private int globalLastKnownPastMessageId = -1;
    private MessagesListAdapter<ChatMessage> adapter;

    private CharSequence myFirstMessage;

    private Autocomplete mentionAutocomplete;
    private LinearLayoutManager layoutManager;
    private boolean lookingIntoFuture = false;

    private int newMessagesCount = 0;
    private Boolean startCallFromNotification;
    private String roomId;
    private boolean voiceOnly;

    private boolean isFirstMessagesProcessing = true;
    private boolean isHelloClicked;

    private final short VIEW_TYPE_DATE_HEADER = 130;
    public ChatController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        UserEntity currentUser = userUtils.getCurrentUser();
        this.conversationName = args.getString(BundleKeys.KEY_CONVERSATION_NAME, "");
        if (args.containsKey(BundleKeys.KEY_USER_ENTITY)) {
            this.conversationUser = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_USER_ENTITY));
        } else {
            this.conversationUser = currentUser;
        }

        this.roomId = args.getString(BundleKeys.KEY_ROOM_ID, "");

        this.roomToken = args.getString(BundleKeys.KEY_ROOM_TOKEN, "");

        if (args.containsKey(BundleKeys.KEY_ACTIVE_CONVERSATION)) {
            this.currentCall = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION));
        }

        this.baseUrl = args.getString(BundleKeys.KEY_MODIFIED_BASE_URL, "");

        if (!TextUtils.isEmpty(baseUrl)) {
            conversationUser.setBaseUrl(baseUrl);
            conversationUser.setUserId("?");
            conversationUser.setDisplayName(currentUser.getDisplayName());
        } else {
            baseUrl = conversationUser.getBaseUrl();
        }

        this.roomPassword = args.getString(BundleKeys.KEY_CONVERSATION_PASSWORD, "");

        if (conversationUser.getUserId().equals("?")) {
            credentials = null;
        } else {
            credentials = ApiUtils.getCredentials(conversationUser.getUserId(), conversationUser.getToken());
        }

        if (args.containsKey(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            this.startCallFromNotification = args.getBoolean(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL);
        }

        this.voiceOnly = args.getBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, false);
    }

    private void getRoomInfo() {
        ncApi.getRoom(credentials, ApiUtils.getRoom(baseUrl, roomToken))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(RoomOverall roomOverall) {
                        conversationName = roomOverall.getOcs().getData().getDisplayName();
                        setTitle();

                        setupMentionAutocomplete();
                        joinRoomWithPassword();

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void handleFromNotification() {
        ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(baseUrl))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(RoomsOverall roomsOverall) {
                        for (Room room : roomsOverall.getOcs().getData()) {
                            if (roomId.equals(room.getRoomId())) {
                                roomToken = room.getToken();
                                conversationName = room.getDisplayName();
                                setTitle();
                                break;
                            }
                        }

                        if (!TextUtils.isEmpty(roomToken)) {
                            setupMentionAutocomplete();
                            joinRoomWithPassword();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_chat, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);

        getActionBar().show();
        boolean adapterWasNull = false;

        sendHiTextView.setText(String.format(getResources().getString(R.string.nc_chat_empty), getResources()
                .getString(R.string.nc_hello)));

        if (adapter == null) {

            loadingProgressBar.setVisibility(View.VISIBLE);

            try {
                cache.evictAll();
            } catch (IOException e) {
                Log.e(TAG, "Failed to evict cache");
            }

            adapterWasNull = true;

            MessageHolders messageHolders = new MessageHolders();
            messageHolders.setIncomingTextConfig(MagicIncomingTextMessageViewHolder.class, R.layout.item_custom_incoming_text_message);
            messageHolders.setOutcomingTextConfig(MagicOutcomingTextMessageViewHolder.class, R.layout.item_custom_outcoming_text_message);

            adapter = new MessagesListAdapter<>(conversationUser.getUserId(), messageHolders, new ImageLoader() {
                @Override
                public void loadImage(ImageView imageView, String url) {
                    GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .load(url)
                            .centerInside()
                            .override(imageView.getMeasuredWidth(), imageView.getMeasuredHeight())
                            .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                            .listener(new RequestListener<Bitmap>() {
                                @Override
                                public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Bitmap> target, boolean isFirstResource) {
                                    TextDrawable drawable = TextDrawable.builder().beginConfig().bold()
                                            .endConfig().buildRound("?", getResources().getColor(R.color.nc_grey));
                                    imageView.setImageDrawable(drawable);
                                    return true;
                                }

                                @Override
                                public boolean onResourceReady(Bitmap resource, Object model, Target<Bitmap> target, DataSource dataSource, boolean isFirstResource) {
                                    return false;
                                }
                            })
                            .into(imageView);
                }
            });
        } else {
            if (adapter.getItemCount() == 0) {
                emptyLayout.setVisibility(View.VISIBLE);
            } else {
                messagesListView.setVisibility(View.VISIBLE);
            }
        }


        messagesListView.setAdapter(adapter);
        adapter.setLoadMoreListener(this);
        adapter.setDateHeadersFormatter(this::format);
        adapter.setOnMessageLongClickListener(this);

        layoutManager = (LinearLayoutManager) messagesListView.getLayoutManager();

        popupBubble.setRecyclerView(messagesListView);

        popupBubble.setPopupBubbleListener(context -> {
            if (newMessagesCount != 0) {
                int scrollPosition;
                if (newMessagesCount - 1 < 0) {
                    scrollPosition = 0;
                } else {
                    scrollPosition = newMessagesCount - 1;
                }
                new Handler().postDelayed(() -> messagesListView.smoothScrollToPosition(scrollPosition), 200);
            }
        });

        messagesListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE) {
                    if (newMessagesCount != 0) {
                        if (layoutManager.findFirstCompletelyVisibleItemPosition() < newMessagesCount) {
                            newMessagesCount = 0;

                            if (popupBubble != null && popupBubble.isShown()) {
                                popupBubble.hide();
                            }
                        }
                    }
                }
            }
        });


        messageInputView.getInputEditText().setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        messageInputView.setInputListener(input -> {
            sendMessage(input, 1);
            return true;
        });

        messageInputView.getButton().setContentDescription(getResources()
                .getString(R.string.nc_description_send_message_button));

        if (adapterWasNull && TextUtils.isEmpty(conversationName) && startCallFromNotification == null) {
            getRoomInfo();
        } else {
            handleFromNotification();
        }
    }


    private void setupMentionAutocomplete() {
        float elevation = 6f;
        Drawable backgroundDrawable = new ColorDrawable(Color.WHITE);
        AutocompletePresenter<Mention> presenter = new MentionAutocompletePresenter(getApplicationContext(), roomToken);
        AutocompleteCallback<Mention> callback = new MentionAutocompleteCallback();

        if (messageInputView != null && messageInputView.getInputEditText() != null) {
            mentionAutocomplete = Autocomplete.<Mention>on(messageInputView.getInputEditText())
                    .with(elevation)
                    .with(backgroundDrawable)
                    .with(new CharPolicy('@'))
                    .with(presenter)
                    .with(callback)
                    .build();
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }

        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomId(roomId);
        ApplicationWideCurrentRoomHolder.getInstance().setInCall(false);
        ApplicationWideCurrentRoomHolder.getInstance().setUserInRoom(conversationUser);

        if (mentionAutocomplete != null && mentionAutocomplete.isPopupShowing()) {
            mentionAutocomplete.dismissPopup();
        }

        if (getActivity() != null) {
            new KeyboardUtils(getActivity(), getView(), false);
        }
    }

    @Override
    protected String getTitle() {
        return conversationName;
    }

    @Override
    public void onDestroy() {
        inChat = false;
        dispose();
        ApplicationWideCurrentRoomHolder.getInstance().clear();
        leaveRoom();
    }

    private void dispose() {
        Disposable disposable;
        for (int i = 0; i < disposableList.size(); i++) {
            if ((disposable = disposableList.get(i)).isDisposed()) {
                disposable.dispose();
            }
        }
    }

    private void startPing() {
        if (!conversationUser.hasSpreedCapabilityWithName("no-ping")) {
            ncApi.pingCall(credentials, ApiUtils.getUrlForCallPing(baseUrl, roomToken))
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .repeatWhen(observable -> observable.delay(5000, TimeUnit.MILLISECONDS))
                    .takeWhile(observable -> inChat)
                    .retry(3, observable -> inChat)
                    .subscribe(new Observer<GenericOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            disposableList.add(d);
                        }

                        @Override
                        public void onNext(GenericOverall genericOverall) {

                        }

                        @Override
                        public void onError(Throwable e) {
                        }

                        @Override
                        public void onComplete() {
                        }
                    });
        }
    }

    @OnClick(R.id.emptyLayout)
    public void sendHello() {
        if (!isHelloClicked) {
            isHelloClicked = true;
            sendMessage(getResources().getString(R.string.nc_hello) + " 👋", 1);
        }
    }

    private void joinRoomWithPassword() {

        if (currentCall == null) {
            ncApi.joinRoom(credentials, ApiUtils.getUrlForSettingMyselfAsActiveParticipant(baseUrl, roomToken), roomPassword)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .retry(3)
                    .subscribe(new Observer<CallOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            disposableList.add(d);
                        }

                        @Override
                        public void onNext(CallOverall callOverall) {
                            inChat = true;
                            currentCall = callOverall.getOcs().getData();
                            startPing();
                            pullChatMessages(0);
                            if (startCallFromNotification != null && startCallFromNotification) {
                                startCallFromNotification = false;
                                startACall(voiceOnly);
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
            inChat = true;
            startPing();
            pullChatMessages(0);
        }
    }

    private void leaveRoom() {
        ncApi.leaveRoom(credentials, ApiUtils.getUrlForSettingMyselfAsActiveParticipant(baseUrl, roomToken))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        getRouter().popToRoot();
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void setSenderId() {
        try {
            final Field senderId = adapter.getClass().getDeclaredField("senderId");
            senderId.setAccessible(true);
            senderId.set(adapter, conversationUser.getUserId());
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "Failed to set sender id");
        } catch (IllegalAccessException e) {
            Log.e(TAG, "Failed to access and set field");
        }
    }

    private void sendMessage(CharSequence message, int attempt) {
        if (attempt < 4) {

            ncApi.sendChatMessage(credentials, ApiUtils.getUrlForChat(baseUrl, roomToken), message, conversationUser
                    .getDisplayName())
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new Observer<GenericOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(GenericOverall genericOverall) {
                            if (conversationUser.getUserId().equals("?") && TextUtils.isEmpty(myFirstMessage.toString())) {
                                myFirstMessage = message;
                            }

                            if (popupBubble != null && popupBubble.isShown()) {
                                popupBubble.hide();
                            }

                            if (messagesListView != null) {
                                messagesListView.smoothScrollToPosition(0);
                            }
                        }

                        @Override
                        public void onError(Throwable e) {
                            if (e instanceof HttpException && ((HttpException) e).code() == 201) {
                                if (conversationUser.getUserId().equals("?") && TextUtils.isEmpty(myFirstMessage.toString())) {
                                    myFirstMessage = message;
                                }

                                if (popupBubble != null && popupBubble.isShown()) {
                                    popupBubble.hide();
                                }

                                messagesListView.smoothScrollToPosition(0);
                            } else {
                                sendMessage(message, attempt + 1);
                            }
                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        }
    }

    private void pullChatMessages(int lookIntoFuture) {
        if (!lookingIntoFuture && lookIntoFuture == 1) {
            lookingIntoFuture = true;
        }

        Map<String, Integer> fieldMap = new HashMap<>();
        fieldMap.put("lookIntoFuture", lookIntoFuture);
        fieldMap.put("limit", 25);

        int lastKnown;
        if (lookIntoFuture == 1) {
            lastKnown = globalLastKnownFutureMessageId;
        } else {
            lastKnown = globalLastKnownPastMessageId;
        }

        if (lastKnown != -1) {
            fieldMap.put("lastKnownMessageId", lastKnown);
        }

        if (lookIntoFuture == 1) {
            ncApi.pullChatMessages(credentials, ApiUtils.getUrlForChat(baseUrl, roomToken), fieldMap)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .takeWhile(observable -> inChat)
                    .retry(3, observable -> inChat)
                    .subscribe(new Observer<Response>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            disposableList.add(d);
                        }

                        @Override
                        public void onNext(Response response) {
                            processMessages(response, true);
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {
                            pullChatMessages(1);
                        }
                    });

        } else {
            ncApi.pullChatMessages(credentials,
                    ApiUtils.getUrlForChat(baseUrl, roomToken), fieldMap)
                    .subscribeOn(Schedulers.newThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .retry(3, observable -> inChat)
                    .subscribe(new Observer<Response>() {
                        @Override
                        public void onSubscribe(Disposable d) {
                            disposableList.add(d);
                        }

                        @Override
                        public void onNext(Response response) {
                            processMessages(response, false);
                        }

                        @Override
                        public void onError(Throwable e) {

                        }

                        @Override
                        public void onComplete() {

                        }
                    });
        }
    }

    private void processMessages(Response response, boolean isFromTheFuture) {
        if (response.code() == 200) {
            ChatOverall chatOverall = (ChatOverall) response.body();
            List<ChatMessage> chatMessageList = chatOverall.getOcs().getData();

            if (isFirstMessagesProcessing) {
                isFirstMessagesProcessing = false;
                if (loadingProgressBar != null) {
                    loadingProgressBar.setVisibility(View.GONE);
                }

                if (chatMessageList.size() == 0) {
                    emptyLayout.setVisibility(View.VISIBLE);
                } else {
                    messagesListView.setVisibility(View.VISIBLE);
                }
            } else {
                if (emptyLayout.getVisibility() != View.GONE) {
                    emptyLayout.setVisibility(View.GONE);
                }
                if (messagesListView.getVisibility() != View.VISIBLE) {
                    messagesListView.setVisibility(View.VISIBLE);
                }
            }

            int countGroupedMessages = 0;
            if (!isFromTheFuture) {

                for (int i = 0; i < chatMessageList.size(); i++) {
                    if (chatMessageList.size() > i + 1) {
                        if (chatMessageList.get(i + 1).getActorId().equals(chatMessageList.get(i).getActorId()) &&
                                countGroupedMessages < 4 && DateFormatter.isSameDay(chatMessageList.get(i).getCreatedAt(),
                                chatMessageList.get(i + 1).getCreatedAt())) {
                            chatMessageList.get(i).setGrouped(true);
                            countGroupedMessages++;
                        } else {
                            countGroupedMessages = 0;
                        }
                    }
                    chatMessageList.get(i).setBaseUrl(conversationUser.getBaseUrl());
                    if (globalLastKnownPastMessageId == -1 || chatMessageList.get(i).getJsonMessageId() <
                            globalLastKnownPastMessageId) {
                        globalLastKnownPastMessageId = chatMessageList.get(i).getJsonMessageId();
                    }

                    if (globalLastKnownFutureMessageId == -1) {
                        if (chatMessageList.get(i).getJsonMessageId() > globalLastKnownFutureMessageId) {
                            globalLastKnownFutureMessageId = chatMessageList.get(i).getJsonMessageId();
                        }
                    }
                }

                adapter.addToEnd(chatMessageList, false);

            } else {

                ChatMessage chatMessage;

                for (int i = 0; i < chatMessageList.size(); i++) {
                    chatMessage = chatMessageList.get(i);

                    chatMessage.setBaseUrl(conversationUser.getBaseUrl());
                    if (conversationUser.getUserId().equals("?") && !TextUtils.isEmpty(myFirstMessage.toString())) {
                        if (chatMessage.getActorType().equals("guests") &&
                                chatMessage.getActorDisplayName().equals(conversationUser.getDisplayName())) {
                            conversationUser.setUserId(chatMessage.getActorId());
                            setSenderId();
                        }
                    }

                    boolean shouldScroll = layoutManager.findFirstVisibleItemPosition() == 0 ||
                            adapter.getItemCount() == 0;

                    if (!shouldScroll && popupBubble != null) {
                        if (!popupBubble.isShown()) {
                            newMessagesCount = 1;
                            popupBubble.show();
                        } else if (popupBubble.isShown()) {
                            newMessagesCount++;
                        }
                    } else {
                        newMessagesCount = 0;
                    }

                    chatMessage.setGrouped(adapter.isPreviousSameAuthor(chatMessage.getActorId(), -1) && (adapter.getSameAuthorLastMessagesCount(chatMessage.getActorId()) % 5) > 0);

                    adapter.addToStart(chatMessage, shouldScroll);
                }

                String xChatLastGivenHeader;
                if (response.headers().size() > 0 && !TextUtils.isEmpty((xChatLastGivenHeader = response.headers().get
                        ("X-Chat-Last-Given")))) {
                    globalLastKnownFutureMessageId = Integer.parseInt(xChatLastGivenHeader);
                }
            }

            if (!lookingIntoFuture) {
                pullChatMessages(1);
            }
        } else if (response.code() == 304 && !isFromTheFuture) {
            if (isFirstMessagesProcessing) {
                isFirstMessagesProcessing  = false;
                loadingProgressBar.setVisibility(View.GONE);

                if (emptyLayout.getVisibility() != View.VISIBLE) {
                    emptyLayout.setVisibility(View.VISIBLE);
                }
            }

            historyRead = true;

            if (!lookingIntoFuture) {
                pullChatMessages(1);
            }
        }
    }

    @Override
    public void onLoadMore(int page, int totalItemsCount) {
        if (!historyRead) {
            pullChatMessages(0);
        }
    }


    @Override
    public String format(Date date) {
        if (DateFormatter.isToday(date)) {
            return getResources().getString(R.string.nc_date_header_today);
        } else if (DateFormatter.isYesterday(date)) {
            return getResources().getString(R.string.nc_date_header_yesterday);
        } else {
            return DateFormatter.format(date, DateFormatter.Template.STRING_DAY_MONTH_YEAR);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.menu_conversation, menu);
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onDestroy();
                return true;
            case R.id.conversation_video_call:
                startACall(false);
                return true;
            case R.id.conversation_voice_call:
                startACall(true);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startACall(boolean isVoiceOnlyCall) {
        if (!isVoiceOnlyCall) {
            Intent videoCallIntent = getIntentForCall(false);
            if (videoCallIntent != null) {
                startActivity(videoCallIntent);
            }
        } else {
            Intent voiceCallIntent = getIntentForCall(true);
            if (voiceCallIntent != null) {
                startActivity(voiceCallIntent);
            }
        }
    }

    private Intent getIntentForCall(boolean isVoiceOnlyCall) {
        if (currentCall != null && !TextUtils.isEmpty(currentCall.getSessionId())) {
            Bundle bundle = new Bundle();
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken);
            bundle.putString(BundleKeys.KEY_ROOM_ID, roomId);
            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, Parcels.wrap(conversationUser));
            bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD, roomPassword);
            bundle.putString(BundleKeys.KEY_CALL_SESSION, currentCall.getSessionId());
            bundle.putString(BundleKeys.KEY_MODIFIED_BASE_URL, baseUrl);

            if (isVoiceOnlyCall) {
                bundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, true);
            }

            if (getActivity() != null) {
                Intent callIntent = new Intent(getActivity(), MagicCallActivity.class);
                callIntent.putExtras(bundle);

                return callIntent;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }


    @Override
    public void onMessageLongClick(IMessage message) {
        if (getActivity() != null) {
            ClipboardManager clipboardManager = (android.content.ClipboardManager)
                    getActivity().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clipData = android.content.ClipData.newPlainText(
                    getResources().getString(R.string.nc_app_name), message.getText());
            if (clipboardManager != null) {
                clipboardManager.setPrimaryClip(clipData);
            }
        }
    }
}
