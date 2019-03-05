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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.*;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.facebook.drawee.view.SimpleDraweeView;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.adapters.messages.MagicIncomingTextMessageViewHolder;
import com.nextcloud.talk.adapters.messages.MagicOutcomingTextMessageViewHolder;
import com.nextcloud.talk.adapters.messages.MagicPreviewMessageViewHolder;
import com.nextcloud.talk.adapters.messages.MagicSystemMessageViewHolder;
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
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.models.json.rooms.RoomsOverall;
import com.nextcloud.talk.presenters.MentionAutocompletePresenter;
import com.nextcloud.talk.utils.*;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder;
import com.nextcloud.talk.utils.text.Spans;
import com.otaliastudios.autocomplete.Autocomplete;
import com.otaliastudios.autocomplete.AutocompleteCallback;
import com.otaliastudios.autocomplete.AutocompletePresenter;
import com.stfalcon.chatkit.commons.ImageLoader;
import com.stfalcon.chatkit.commons.models.IMessage;
import com.stfalcon.chatkit.messages.MessageHolders;
import com.stfalcon.chatkit.messages.MessageInput;
import com.stfalcon.chatkit.messages.MessagesList;
import com.stfalcon.chatkit.messages.MessagesListAdapter;
import com.stfalcon.chatkit.utils.DateFormatter;
import com.vanniktech.emoji.EmojiEditText;
import com.vanniktech.emoji.EmojiImageView;
import com.vanniktech.emoji.EmojiPopup;
import com.vanniktech.emoji.emoji.Emoji;
import com.vanniktech.emoji.listeners.OnEmojiClickListener;
import com.vanniktech.emoji.listeners.OnEmojiPopupDismissListener;
import com.vanniktech.emoji.listeners.OnEmojiPopupShownListener;
import com.webianks.library.PopupBubble;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import org.parceler.Parcels;
import retrofit2.HttpException;
import retrofit2.Response;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;

@AutoInjector(NextcloudTalkApplication.class)
public class ChatController extends BaseController implements MessagesListAdapter.OnLoadMoreListener,
        MessagesListAdapter.Formatter<Date>, MessagesListAdapter.OnMessageLongClickListener, MessageHolders.ContentChecker {
    private static final String TAG = "ChatController";
    private static final byte CONTENT_TYPE_SYSTEM_MESSAGE = 1;
    @Inject
    NcApi ncApi;
    @Inject
    UserUtils userUtils;
    @Inject
    AppPreferences appPreferences;
    @BindView(R.id.messagesListView)
    MessagesList messagesListView;
    @BindView(R.id.messageInputView)
    MessageInput messageInputView;
    @BindView(R.id.messageInput)
    EmojiEditText messageInput;
    @BindView(R.id.popupBubbleView)
    PopupBubble popupBubble;
    @BindView(R.id.emptyLayout)
    RelativeLayout emptyLayout;
    @BindView(R.id.sendHiTextView)
    TextView sendHiTextView;
    @BindView(R.id.progressBar)
    ProgressBar loadingProgressBar;
    @BindView(R.id.smileyButton)
    ImageButton smileyButton;
    private List<Disposable> disposableList = new ArrayList<>();
    private String conversationName;
    private String roomToken;
    private UserEntity conversationUser;
    private String roomPassword;
    private String credentials;
    private Call currentCall;
    private boolean inChat = false;
    private boolean historyRead = false;
    private int globalLastKnownFutureMessageId = -1;
    private int globalLastKnownPastMessageId = -1;
    private MessagesListAdapter<ChatMessage> adapter;
    private Autocomplete mentionAutocomplete;
    private LinearLayoutManager layoutManager;
    private boolean lookingIntoFuture = false;
    private int newMessagesCount = 0;
    private Boolean startCallFromNotification = null;
    private String roomId;
    private boolean voiceOnly;
    private boolean isFirstMessagesProcessing = true;
    private boolean isHelloClicked;
    private boolean isLeavingForConversation;
    private boolean isLinkPreviewAllowed;
    private boolean wasDetached;
    private EmojiPopup emojiPopup;

    private CharSequence myFirstMessage;

    public ChatController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        this.conversationName = args.getString(BundleKeys.KEY_CONVERSATION_NAME, "");
        this.conversationUser = args.getParcelable(BundleKeys.KEY_USER_ENTITY);
        this.roomId = args.getString(BundleKeys.KEY_ROOM_ID, "");
        this.roomToken = args.getString(BundleKeys.KEY_ROOM_TOKEN, "");

        if (args.containsKey(BundleKeys.KEY_ACTIVE_CONVERSATION)) {
            this.currentCall = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ACTIVE_CONVERSATION));
        }

        this.roomPassword = args.getString(BundleKeys.KEY_CONVERSATION_PASSWORD, "");

        if (conversationUser.getUserId().equals("?")) {
            credentials = null;
        } else {
            credentials = ApiUtils.getCredentials(conversationUser.getUsername(), conversationUser.getToken());
        }

        if (args.containsKey(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL)) {
            this.startCallFromNotification = args.getBoolean(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL);
        }

        this.voiceOnly = args.getBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, false);
    }

    private void getRoomInfo() {
        ncApi.getRoom(credentials, ApiUtils.getRoom(conversationUser.getBaseUrl(), roomToken))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposableList.add(d);
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
        ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(conversationUser.getBaseUrl()))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposableList.add(d);
                    }

                    @Override
                    public void onNext(RoomsOverall roomsOverall) {
                        for (Conversation conversation : roomsOverall.getOcs().getData()) {
                            if (roomId.equals(conversation.getRoomId())) {
                                roomToken = conversation.getToken();
                                conversationName = conversation.getDisplayName();
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

            adapterWasNull = true;

            MessageHolders messageHolders = new MessageHolders();
            messageHolders.setIncomingTextConfig(MagicIncomingTextMessageViewHolder.class, R.layout.item_custom_incoming_text_message);
            messageHolders.setOutcomingTextConfig(MagicOutcomingTextMessageViewHolder.class, R.layout.item_custom_outcoming_text_message);

            messageHolders.setIncomingImageConfig(MagicPreviewMessageViewHolder.class, R.layout.item_custom_incoming_preview_message);
            messageHolders.setOutcomingImageConfig(MagicPreviewMessageViewHolder.class, R.layout.item_custom_outcoming_preview_message);

            messageHolders.registerContentType(CONTENT_TYPE_SYSTEM_MESSAGE, MagicSystemMessageViewHolder.class,
                    R.layout.item_system_message, MagicSystemMessageViewHolder.class, R.layout.item_system_message,
                    this);

            adapter = new MessagesListAdapter<>(conversationUser.getUserId(), messageHolders, new ImageLoader() {
                @Override
                public void loadImage(SimpleDraweeView imageView, String url) {
                    DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                            .setImageRequest(DisplayUtils.getImageRequestForUrl(url))
                            .setControllerListener(DisplayUtils.getImageControllerListener(imageView))
                            .setOldController(imageView.getController())
                            .setAutoPlayAnimations(true)
                            .build();
                    imageView.setController(draweeController);
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


        InputFilter[] filters = new InputFilter[1];
        filters[0] = new InputFilter.LengthFilter(1000);
        messageInput.setFilters(filters);

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 1000) {
                    messageInput.setError(getResources().getString(R.string.nc_limit_hit));
                } else {
                    messageInput.setError(null);
                }

                Editable editable = messageInput.getEditableText();
                Spans.MentionSpan[] mentionSpans = editable.getSpans(0, messageInput.length(), Spans.MentionSpan.class);
                Spans.MentionSpan mentionSpan;
                for (int i = 0; i < mentionSpans.length; i++) {
                    mentionSpan = mentionSpans[i];
                    if (start >= editable.getSpanStart(mentionSpan) && start < editable.getSpanEnd(mentionSpan)) {
                        editable.removeSpan(mentionSpan);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        messageInputView.getButton().setOnClickListener(v -> submitMessage());
        messageInputView.getButton().setContentDescription(getResources()
                .getString(R.string.nc_description_send_message_button));

        if (!conversationUser.getUserId().equals("?") && conversationUser.hasSpreedCapabilityWithName("mention-flag") && getActivity() != null) {
            getActivity().findViewById(R.id.toolbar).setOnClickListener(v -> showConversationInfoScreen());
        }

        if (adapterWasNull) {
            // we're starting
            if (TextUtils.isEmpty(roomToken)) {
                handleFromNotification();
            } else if (TextUtils.isEmpty(conversationName)) {
                getRoomInfo();
            } else {
                setupMentionAutocomplete();
                joinRoomWithPassword();
            }
        }
    }


    private void showConversationInfoScreen() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, conversationUser);
        bundle.putString(BundleKeys.KEY_BASE_URL, conversationUser.getBaseUrl());
        bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken);
        getRouter().pushController((RouterTransaction.with(new ConversationInfoController(bundle))
                .pushChangeHandler(new HorizontalChangeHandler())
                .popChangeHandler(new HorizontalChangeHandler())));

    }

    private void setupMentionAutocomplete() {
        float elevation = 6f;
        Drawable backgroundDrawable = new ColorDrawable(Color.WHITE);
        AutocompletePresenter<Mention> presenter = new MentionAutocompletePresenter(getApplicationContext(), roomToken);
        AutocompleteCallback<Mention> callback = new MentionAutocompleteCallback();

        if (mentionAutocomplete == null && messageInput != null) {
            mentionAutocomplete = Autocomplete.<Mention>on(messageInput)
                    .with(elevation)
                    .with(backgroundDrawable)
                    .with(new MagicCharPolicy('@'))
                    .with(presenter)
                    .with(callback)
                    .build();
        }
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);

        isLeavingForConversation = false;
        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomId(roomId);
        ApplicationWideCurrentRoomHolder.getInstance().setCurrentRoomToken(roomId);
        ApplicationWideCurrentRoomHolder.getInstance().setInCall(false);
        ApplicationWideCurrentRoomHolder.getInstance().setUserInRoom(conversationUser);

        isLinkPreviewAllowed = appPreferences.getAreLinkPreviewsAllowed();

        emojiPopup = EmojiPopup.Builder.fromRootView(view).setOnEmojiPopupShownListener(new OnEmojiPopupShownListener() {
            @Override
            public void onEmojiPopupShown() {
                if (getResources() != null) {
                    smileyButton.setColorFilter(getResources().getColor(R.color.colorPrimary),
                            PorterDuff.Mode.SRC_IN);
                }
            }
        }).setOnEmojiPopupDismissListener(new OnEmojiPopupDismissListener() {
            @Override
            public void onEmojiPopupDismiss() {
                if (smileyButton != null) {
                    smileyButton.clearColorFilter();
                }
            }
        }).setOnEmojiClickListener(new OnEmojiClickListener() {
            @Override
            public void onEmojiClick(@NonNull EmojiImageView emoji, @NonNull Emoji imageView) {
                messageInput.getEditableText().append(" ");
            }
        }).build(messageInput);

        if (mentionAutocomplete != null && mentionAutocomplete.isPopupShowing()) {
            mentionAutocomplete.dismissPopup();
        }

        if (getActivity() != null) {
            new KeyboardUtils(getActivity(), getView(), false);
        }

        NotificationUtils.cancelExistingNotifications(getApplicationContext(), conversationUser);

        if (inChat) {
            if (wasDetached & conversationUser.hasSpreedCapabilityWithName("no-ping")) {
                wasDetached = false;
                joinRoomWithPassword();
            }
        }
    }

    @Override
    protected void onDetach(@NonNull View view) {
        super.onDetach(view);
        ApplicationWideCurrentRoomHolder.getInstance().clear();

        if (conversationUser.hasSpreedCapabilityWithName("no-ping")
                && getActivity() != null && !getActivity().isChangingConfigurations() && !isLeavingForConversation) {
            wasDetached = true;
            leaveRoom();
        }
    }

    @Override
    protected String getTitle() {
        return conversationName;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (getActivity() != null) {
            getActivity().findViewById(R.id.toolbar).setOnClickListener(null);
        }

        adapter = null;
        inChat = false;
    }

    private void dispose() {
        Disposable disposable;
        for (int i = 0; i < disposableList.size(); i++) {
            if (!(disposable = disposableList.get(i)).isDisposed()) {
                disposable.dispose();
            }
        }
    }

    private void startPing() {
        if (!conversationUser.hasSpreedCapabilityWithName("no-ping")) {
            ncApi.pingCall(credentials, ApiUtils.getUrlForCallPing(conversationUser.getBaseUrl(), roomToken))
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

    @OnClick(R.id.smileyButton)
    void onSmileyClick() {
        emojiPopup.toggle();
    }

    @OnClick(R.id.emptyLayout)
    void sendHello() {
        if (!isHelloClicked) {
            isHelloClicked = true;
            sendMessage(getResources().getString(R.string.nc_hello) + " 👋");
        }
    }

    private void joinRoomWithPassword() {

        if (currentCall == null) {
            ncApi.joinRoom(credentials,
                    ApiUtils.getUrlForSettingMyselfAsActiveParticipant(conversationUser.getBaseUrl(), roomToken), roomPassword)
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
                            ApplicationWideCurrentRoomHolder.getInstance().setSession(currentCall.getSessionId());
                            startPing();
                            if (isFirstMessagesProcessing) {
                                pullChatMessages(0);
                            } else {
                                pullChatMessages(1);
                            }
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
            ApplicationWideCurrentRoomHolder.getInstance().setSession(currentCall.getSessionId());
            startPing();
            if (isFirstMessagesProcessing) {
                pullChatMessages(0);
            } else {
                pullChatMessages(1);
            }
        }
    }

    private void leaveRoom() {
        ncApi.leaveRoom(credentials,
                ApiUtils.getUrlForSettingMyselfAsActiveParticipant(conversationUser.getBaseUrl(),
                        roomToken))
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposableList.add(d);
                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        dispose();
                        currentCall = null;
                        if (!isDestroyed() && !isBeingDestroyed() && !wasDetached) {
                            getRouter().popCurrentController();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {
                        dispose();
                    }
                });
    }

    private void setSenderId() {
        try {
            final Field senderId = adapter.getClass().getDeclaredField("senderId");
            senderId.setAccessible(true);
            senderId.set(adapter, conversationUser.getUserId());
        } catch (NoSuchFieldException e) {
            Log.w(TAG, "Failed to set sender id");
        } catch (IllegalAccessException e) {
            Log.w(TAG, "Failed to access and set field");
        }
    }

    private void submitMessage() {
        final Editable editable = messageInput.getEditableText();
        Spans.MentionSpan mentionSpans[] = editable.getSpans(0, editable.length(), Spans.MentionSpan.class);
        Spans.MentionSpan mentionSpan;
        for (int i = 0; i < mentionSpans.length; i++) {
            mentionSpan = mentionSpans[i];
            editable.replace(editable.getSpanStart(mentionSpan), editable.getSpanEnd(mentionSpan), "@" + mentionSpan.getId());
        }

        messageInput.setText("");
        sendMessage(editable);
    }

    private void sendMessage(CharSequence message) {

        ncApi.sendChatMessage(credentials, ApiUtils.getUrlForChat(conversationUser.getBaseUrl(), roomToken),
                message, conversationUser
                        .getDisplayName())
                .subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<GenericOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(GenericOverall genericOverall) {
                        myFirstMessage = message;

                        if (popupBubble != null && popupBubble.isShown()) {
                            popupBubble.hide();
                        }

                        if (messagesListView != null) {
                            messagesListView.smoothScrollToPosition(0);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (e instanceof HttpException) {
                            int code = ((HttpException) e).code();
                            if (Integer.toString(code).startsWith("2")) {
                                myFirstMessage = message;

                                if (popupBubble != null && popupBubble.isShown()) {
                                    popupBubble.hide();
                                }

                                messagesListView.smoothScrollToPosition(0);
                            }
                        }
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void pullChatMessages(int lookIntoFuture) {
        if (!inChat) {
            return;
        }

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

        if (!wasDetached) {
            if (lookIntoFuture == 1) {
                ncApi.pullChatMessages(credentials, ApiUtils.getUrlForChat(conversationUser.getBaseUrl(),
                        roomToken),
                        fieldMap)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .takeWhile(observable -> inChat && !wasDetached)
                        .retry(3, observable -> inChat && !wasDetached)
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
                        ApiUtils.getUrlForChat(conversationUser.getBaseUrl(), roomToken), fieldMap)
                        .subscribeOn(Schedulers.newThread())
                        .observeOn(AndroidSchedulers.mainThread())
                        .retry(3, observable -> inChat && !wasDetached)
                        .takeWhile(observable -> inChat && !wasDetached)
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
    }

    private void processMessages(Response response, boolean isFromTheFuture) {
        if (response.code() == 200) {
            ChatOverall chatOverall = (ChatOverall) response.body();
            List<ChatMessage> chatMessageList = chatOverall.getOcs().getData();

            if (isFirstMessagesProcessing) {
                NotificationUtils.cancelExistingNotifications(getApplicationContext(), conversationUser);

                isFirstMessagesProcessing = false;
                if (loadingProgressBar != null) {
                    loadingProgressBar.setVisibility(View.GONE);
                }

                if (chatMessageList.size() == 0) {
                    if (emptyLayout != null) {
                        emptyLayout.setVisibility(View.VISIBLE);
                    }

                    if (messagesListView != null) {
                        messagesListView.setVisibility(View.GONE);
                    }

                } else {
                    if (emptyLayout != null) {
                        emptyLayout.setVisibility(View.GONE);
                    }

                    if (messagesListView != null) {
                        messagesListView.setVisibility(View.VISIBLE);
                    }

                }
            } else {
                if (emptyLayout != null) {
                    emptyLayout.setVisibility(View.GONE);
                }

                if (messagesListView != null) {
                    messagesListView.setVisibility(View.VISIBLE);
                }
            }

            int countGroupedMessages = 0;
            if (!isFromTheFuture) {

                for (int i = 0; i < chatMessageList.size(); i++) {
                    if (chatMessageList.size() > i + 1) {
                        if (TextUtils.isEmpty(chatMessageList.get(i).getSystemMessage()) &&
                                TextUtils.isEmpty(chatMessageList.get(i + 1).getSystemMessage()) &&
                                chatMessageList.get(i + 1).getActorId().equals(chatMessageList.get(i).getActorId()) &&
                                countGroupedMessages < 4 && DateFormatter.isSameDay(chatMessageList.get(i).getCreatedAt(),
                                chatMessageList.get(i + 1).getCreatedAt())) {
                            chatMessageList.get(i).setGrouped(true);
                            countGroupedMessages++;
                        } else {
                            countGroupedMessages = 0;
                        }
                    }

                    ChatMessage chatMessage = chatMessageList.get(i);
                    chatMessage.setLinkPreviewAllowed(isLinkPreviewAllowed);
                    chatMessage.setBaseUrl(conversationUser.getBaseUrl());
                    chatMessage.setActiveUserId(conversationUser.getUserId());
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

                if (adapter != null) {
                    adapter.addToEnd(chatMessageList, false);
                }

            } else {

                ChatMessage chatMessage;

                for (int i = 0; i < chatMessageList.size(); i++) {
                    chatMessage = chatMessageList.get(i);

                    chatMessage.setBaseUrl(conversationUser.getBaseUrl());
                    chatMessage.setActiveUserId(conversationUser.getUserId());
                    chatMessage.setLinkPreviewAllowed(isLinkPreviewAllowed);

                    // if credentials are empty, we're acting as a guest
                    if (TextUtils.isEmpty(credentials) && myFirstMessage != null && !TextUtils.isEmpty(myFirstMessage.toString())) {
                        if (chatMessage.getActorType().equals("guests")) {
                            conversationUser.setUserId(chatMessage.getActorId());
                            setSenderId();
                        }
                    }

                    boolean shouldScroll = layoutManager.findFirstVisibleItemPosition() == 0 ||
                            (adapter != null && adapter.getItemCount() == 0);

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

            if (!lookingIntoFuture && inChat) {
                pullChatMessages(1);
            }
        } else if (response.code() == 304 && !isFromTheFuture) {
            if (isFirstMessagesProcessing) {
                NotificationUtils.cancelExistingNotifications(getApplicationContext(), conversationUser);

                isFirstMessagesProcessing = false;
                if (loadingProgressBar != null) {
                    loadingProgressBar.setVisibility(View.GONE);
                }

                if (emptyLayout != null && emptyLayout.getVisibility() != View.VISIBLE) {
                    emptyLayout.setVisibility(View.VISIBLE);
                }
            }

            historyRead = true;

            if (!lookingIntoFuture && inChat) {
                pullChatMessages(1);
            }
        }
    }

    @Override
    public void onLoadMore(int page, int totalItemsCount) {
        if (!historyRead && inChat) {
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
        if (conversationUser.getUserId().equals("?")) {
            menu.removeItem(R.id.conversation_info);
        }
    }


    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getRouter().popCurrentController();
                return true;
            case R.id.conversation_video_call:
                startACall(false);
                return true;
            case R.id.conversation_voice_call:
                startACall(true);
                return true;
            case R.id.conversation_info:
                showConversationInfoScreen();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void startACall(boolean isVoiceOnlyCall) {
        isLeavingForConversation = true;
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
        if (currentCall != null) {
            Bundle bundle = new Bundle();
            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, roomToken);
            bundle.putString(BundleKeys.KEY_ROOM_ID, roomId);
            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, conversationUser);
            bundle.putString(BundleKeys.KEY_CONVERSATION_PASSWORD, roomPassword);
            bundle.putString(BundleKeys.KEY_MODIFIED_BASE_URL, conversationUser.getBaseUrl());

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

    @Override
    public boolean hasContentFor(IMessage message, byte type) {
        switch (type) {
            case CONTENT_TYPE_SYSTEM_MESSAGE:
                return !TextUtils.isEmpty(message.getSystemMessage());
        }

        return false;
    }
}
