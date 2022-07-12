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

package com.nextcloud.talk.jobs;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.CallActivity;
import com.nextcloud.talk.activities.MainActivity;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.SignatureVerification;
import com.nextcloud.talk.models.database.ArbitraryStorageEntity;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatUtils;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.notifications.NotificationOverall;
import com.nextcloud.talk.models.json.push.DecryptedPushMessage;
import com.nextcloud.talk.models.json.push.NotificationUser;
import com.nextcloud.talk.receivers.DirectReplyReceiver;
import com.nextcloud.talk.receivers.MarkAsReadReceiver;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DoNotDisturbUtils;
import com.nextcloud.talk.utils.NotificationUtils;
import com.nextcloud.talk.utils.PushUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder;

import org.parceler.Parcels;

import java.io.IOException;
import java.net.CookieManager;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Objects;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.MessagingStyle;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.app.RemoteInput;
import androidx.emoji.text.EmojiCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;

@AutoInjector(NextcloudTalkApplication.class)
public class NotificationWorker extends Worker {
    public static final String TAG = "NotificationWorker";
    private static final String CHAT = "chat";
    private static final String ROOM = "room";

    @Inject
    AppPreferences appPreferences;

    @Inject
    ArbitraryStorageUtils arbitraryStorageUtils;

    @Inject
    Retrofit retrofit;

    @Inject
    OkHttpClient okHttpClient;

    private NcApi ncApi;

    private DecryptedPushMessage decryptedPushMessage;
    private Context context;
    private SignatureVerification signatureVerification;
    private String conversationType = "one2one";

    private String credentials;
    private boolean muteCall = false;
    private boolean importantConversation = false;

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private void showNotificationForCallWithNoPing(Intent intent) {
        UserEntity userEntity = signatureVerification.getUserEntity();

        ArbitraryStorageEntity arbitraryStorageEntity;

        if ((arbitraryStorageEntity = arbitraryStorageUtils.getStorageSetting(
            userEntity.getId(),
            "important_conversation",
            intent.getExtras().getString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN()))) != null) {
            importantConversation = Boolean.parseBoolean(arbitraryStorageEntity.getValue());
        }

        int apiVersion = ApiUtils.getConversationApiVersion(userEntity, new int[] {ApiUtils.APIv4, 1});

        ncApi.getRoom(credentials, ApiUtils.getUrlForRoom(apiVersion, userEntity.getBaseUrl(),
                intent.getExtras().getString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN())))
                .blockingSubscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(RoomOverall roomOverall) {
                        Conversation conversation = roomOverall.getOcs().getData();

                        intent.putExtra(BundleKeys.INSTANCE.getKEY_ROOM(), Parcels.wrap(conversation));
                        if (conversation.getType().equals(Conversation.ConversationType.ROOM_TYPE_ONE_TO_ONE_CALL) ||
                                (!TextUtils.isEmpty(conversation.getObjectType()) && "share:password".equals
                                        (conversation.getObjectType()))) {
                            context.startActivity(intent);
                        } else {
                            if (conversation.getType().equals(Conversation.ConversationType.ROOM_GROUP_CALL)) {
                                conversationType = "group";
                            } else {
                                conversationType = "public";
                            }
                            if (decryptedPushMessage.getNotificationId() != Long.MIN_VALUE) {
                                showNotificationWithObjectData(intent);
                            } else {
                                showNotification(intent);
                            }
                        }

                        muteCall = !(conversation.getNotificationCalls() == 1);
                    }

                    @Override
                    public void onError(Throwable e) {
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private void showNotificationWithObjectData(Intent intent) {
        UserEntity userEntity = signatureVerification.getUserEntity();
        ncApi.getNotification(credentials, ApiUtils.getUrlForNotificationWithId(userEntity.getBaseUrl(),
                Long.toString(decryptedPushMessage.getNotificationId())))
                .blockingSubscribe(new Observer<NotificationOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(NotificationOverall notificationOverall) {
                        com.nextcloud.talk.models.json.notifications.Notification notification =
                                notificationOverall.getOcs().getNotification();

                        if (notification.getMessageRichParameters() != null &&
                                notification.getMessageRichParameters().size() > 0) {
                            decryptedPushMessage.setText(ChatUtils.Companion.getParsedMessage(
                                notification.getMessageRich(),
                                notification.getMessageRichParameters()));
                        } else {
                            decryptedPushMessage.setText(notification.getMessage());
                        }

                        HashMap<String, HashMap<String, String>> subjectRichParameters = notification
                                .getSubjectRichParameters();

                        decryptedPushMessage.setTimestamp(notification.getDatetime().getMillis());

                        if (subjectRichParameters != null && subjectRichParameters.size() > 0) {
                            HashMap<String, String> callHashMap = subjectRichParameters.get("call");
                            HashMap<String, String> userHashMap = subjectRichParameters.get("user");
                            HashMap<String, String> guestHashMap = subjectRichParameters.get("guest");

                            if (callHashMap != null && callHashMap.size() > 0 && callHashMap.containsKey("name")) {
                                if (subjectRichParameters.containsKey("reaction")) {
                                    decryptedPushMessage.setSubject("");
                                    decryptedPushMessage.setText(notification.getSubject());
                                } else if (Objects.equals(notification.getObjectType(), "chat")) {
                                    decryptedPushMessage.setSubject(Objects.requireNonNull(callHashMap.get("name")));
                                } else {
                                    decryptedPushMessage.setSubject(Objects.requireNonNull(notification.getSubject()));
                                }

                                if (callHashMap.containsKey("call-type")) {
                                    conversationType = callHashMap.get("call-type");
                                }
                            }

                            NotificationUser notificationUser = new NotificationUser();
                            if (userHashMap != null && !userHashMap.isEmpty()) {
                                notificationUser.setId(userHashMap.get("id"));
                                notificationUser.setType(userHashMap.get("type"));
                                notificationUser.setName(userHashMap.get("name"));
                                decryptedPushMessage.setNotificationUser(notificationUser);
                            } else if (guestHashMap != null && !guestHashMap.isEmpty()) {
                                notificationUser.setId(guestHashMap.get("id"));
                                notificationUser.setType(guestHashMap.get("type"));
                                notificationUser.setName(guestHashMap.get("name"));
                                decryptedPushMessage.setNotificationUser(notificationUser);
                            }
                        }

                        decryptedPushMessage.setObjectId(notification.getObjectId());

                        showNotification(intent);
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }

    private void showNotification(Intent intent) {
        int smallIcon;
        Bitmap largeIcon;
        String category;
        int priority = Notification.PRIORITY_HIGH;

        smallIcon = R.drawable.ic_logo;

        if (CHAT.equals(decryptedPushMessage.getType()) || ROOM.equals(decryptedPushMessage.getType())) {
            category = Notification.CATEGORY_MESSAGE;
        } else {
            category = Notification.CATEGORY_CALL;
        }

        switch (conversationType) {
            case "one2one":
                decryptedPushMessage.setSubject("");
            case "group":
                largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_people_group_black_24px);
                break;
            case "public":
                largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_link_black_24px);
                break;
            default:
                // assuming one2one
                if (CHAT.equals(decryptedPushMessage.getType()) || ROOM.equals(decryptedPushMessage.getType())) {
                    largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_comment);
                } else {
                    largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_call_black_24dp);
                }
        }

        // Use unique request code to make sure that a new PendingIntent gets created for each notification
        // See https://github.com/nextcloud/talk-android/issues/2111
        int requestCode = (int) System.currentTimeMillis();
        int intentFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            intentFlag = PendingIntent.FLAG_MUTABLE;
        } else {
            intentFlag = 0;
        }
        PendingIntent pendingIntent = PendingIntent.getActivity(context, requestCode, intent, intentFlag);

        Uri uri = Uri.parse(signatureVerification.getUserEntity().getBaseUrl());
        String baseUrl = uri.getHost();

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, "1")
                .setLargeIcon(largeIcon)
                .setSmallIcon(smallIcon)
                .setCategory(category)
                .setPriority(priority)
                .setSubText(baseUrl)
                .setWhen(decryptedPushMessage.getTimestamp())
                .setShowWhen(true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (!TextUtils.isEmpty(decryptedPushMessage.getSubject())) {
            notificationBuilder.setContentTitle(EmojiCompat.get().process(decryptedPushMessage.getSubject()));
        }

        if (!TextUtils.isEmpty(decryptedPushMessage.getText())) {
            notificationBuilder.setContentText(EmojiCompat.get().process(decryptedPushMessage.getText()));
        }

        if (Build.VERSION.SDK_INT >= 23) {
            // This method should exist since API 21, but some phones don't have it
            // So as a safeguard, we don't use it until 23
            notificationBuilder.setColor(context.getResources().getColor(R.color.colorPrimary));
        }

        Bundle notificationInfo = new Bundle();
        notificationInfo.putLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(),
                                 signatureVerification.getUserEntity().getId());
        // could be an ID or a TOKEN
        notificationInfo.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(),
                                   decryptedPushMessage.getId());
        notificationInfo.putLong(BundleKeys.INSTANCE.getKEY_NOTIFICATION_ID(),
                                 decryptedPushMessage.getNotificationId());
        notificationBuilder.setExtras(notificationInfo);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (CHAT.equals(decryptedPushMessage.getType()) || ROOM.equals(decryptedPushMessage.getType())) {
                notificationBuilder.setChannelId(NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES_V4);
            }
        } else {
            // red color for the lights
            notificationBuilder.setLights(0xFFFF0000, 200, 200);
        }

        notificationBuilder.setContentIntent(pendingIntent);

        String groupName = signatureVerification.getUserEntity().getId() + "@" + decryptedPushMessage.getId();
        notificationBuilder.setGroup(Long.toString(calculateCRC32(groupName)));

        StatusBarNotification activeStatusBarNotification =
                NotificationUtils.INSTANCE.findNotificationForRoom(context,
                        signatureVerification.getUserEntity(), decryptedPushMessage.getId());

        // NOTE - systemNotificationId is an internal ID used on the device only.
        // It is NOT the same as the notification ID used in communication with the server.
        int systemNotificationId;
        if (activeStatusBarNotification != null) {
            systemNotificationId = activeStatusBarNotification.getId();
        } else {
            systemNotificationId = (int) calculateCRC32(String.valueOf(System.currentTimeMillis()));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
            CHAT.equals(decryptedPushMessage.getType()) &&
            decryptedPushMessage.getNotificationUser() != null) {
            prepareChatNotification(notificationBuilder, activeStatusBarNotification, systemNotificationId);
        }

        sendNotification(systemNotificationId, notificationBuilder.build());
    }

    private long calculateCRC32(String s) {
        CRC32 crc32 = new CRC32();
        crc32.update(s.getBytes());
        return crc32.getValue();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void prepareChatNotification(NotificationCompat.Builder notificationBuilder,
                                         StatusBarNotification activeStatusBarNotification,
                                         int systemNotificationId) {

        final NotificationUser notificationUser = decryptedPushMessage.getNotificationUser();
        final String userType = notificationUser.getType();

        MessagingStyle style = null;
        if (activeStatusBarNotification != null) {
            style = MessagingStyle.extractMessagingStyleFromNotification(activeStatusBarNotification.getNotification());
        }

        Person.Builder person =
                new Person.Builder()
                    .setKey(signatureVerification.getUserEntity().getId() + "@" + notificationUser.getId())
                    .setName(EmojiCompat.get().process(notificationUser.getName()))
                    .setBot("bot".equals(userType));

        notificationBuilder.setOnlyAlertOnce(true);
        addReplyAction(notificationBuilder, systemNotificationId);
        addMarkAsReadAction(notificationBuilder, systemNotificationId);

        if ("user".equals(userType) || "guest".equals(userType)) {
            String baseUrl = signatureVerification.getUserEntity().getBaseUrl();
            String avatarUrl = "user".equals(userType) ?
                ApiUtils.getUrlForAvatar(baseUrl, notificationUser.getId(), false) :
                ApiUtils.getUrlForGuestAvatar(baseUrl, notificationUser.getName(), false);
            person.setIcon(NotificationUtils.INSTANCE.loadAvatarSync(avatarUrl));
        }

        notificationBuilder.setStyle(getStyle(person.build(), style));
    }

    private PendingIntent buildIntentForAction(Class<?> cls, int systemNotificationId, int messageId) {
        Intent actualIntent = new Intent(context, cls);

        // NOTE - systemNotificationId is an internal ID used on the device only.
        // It is NOT the same as the notification ID used in communication with the server.
        actualIntent.putExtra(BundleKeys.INSTANCE.getKEY_SYSTEM_NOTIFICATION_ID(), systemNotificationId);
        actualIntent.putExtra(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(),
                              Objects.requireNonNull(signatureVerification.getUserEntity()).getId());
        actualIntent.putExtra(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), decryptedPushMessage.getId());
        actualIntent.putExtra(BundleKeys.KEY_MESSAGE_ID, messageId);

        int intentFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            intentFlag = PendingIntent.FLAG_MUTABLE|PendingIntent.FLAG_UPDATE_CURRENT;
        } else {
            intentFlag = PendingIntent.FLAG_UPDATE_CURRENT;
        }

        return PendingIntent.getBroadcast(context, systemNotificationId, actualIntent, intentFlag);
    }

    private void addMarkAsReadAction(NotificationCompat.Builder notificationBuilder, int systemNotificationId) {
        if (decryptedPushMessage.getObjectId() != null) {
            int messageId = 0;
            try {
                messageId = parseMessageId(decryptedPushMessage.getObjectId());
            } catch (NumberFormatException nfe) {
                Log.e(TAG, "Failed to parse messageId from objectId, skip adding mark-as-read action.", nfe);
                return;
            }

            // Build a PendingIntent for the mark as read action
            PendingIntent pendingIntent = buildIntentForAction(MarkAsReadReceiver.class,
                                                               systemNotificationId,
                                                               messageId);

            NotificationCompat.Action action =
                new NotificationCompat.Action.Builder(R.drawable.ic_eye,
                                                      context.getResources().getString(R.string.nc_mark_as_read),
                                                      pendingIntent)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
                    .setShowsUserInterface(false)
                    .build();

            notificationBuilder.addAction(action);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void addReplyAction(NotificationCompat.Builder notificationBuilder, int systemNotificationId) {
        String replyLabel = context.getResources().getString(R.string.nc_reply);

        RemoteInput remoteInput = new RemoteInput.Builder(NotificationUtils.KEY_DIRECT_REPLY)
            .setLabel(replyLabel)
            .build();

        // Build a PendingIntent for the reply action
        PendingIntent replyPendingIntent = buildIntentForAction(DirectReplyReceiver.class, systemNotificationId, 0);

        NotificationCompat.Action replyAction =
            new NotificationCompat.Action.Builder(R.drawable.ic_reply, replyLabel, replyPendingIntent)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setShowsUserInterface(false)
                // Allows system to generate replies by context of conversation.
                // https://developer.android.com/reference/androidx/core/app/NotificationCompat.Action.Builder#setAllowGeneratedReplies(boolean)
                // Good question is - do we really want it?
                .setAllowGeneratedReplies(true)
                .addRemoteInput(remoteInput)
                .build();

        notificationBuilder.addAction(replyAction);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private MessagingStyle getStyle(Person person, @Nullable MessagingStyle style) {
        MessagingStyle newStyle = new MessagingStyle(person);

        newStyle.setConversationTitle(decryptedPushMessage.getSubject());
        newStyle.setGroupConversation(!"one2one".equals(conversationType));

        if (style != null) {
            style.getMessages().forEach(message -> newStyle.addMessage(
                new MessagingStyle.Message(message.getText(),
                                           message.getTimestamp(),
                                           message.getPerson())));
        }

        newStyle.addMessage(decryptedPushMessage.getText(), decryptedPushMessage.getTimestamp(), person);
        return newStyle;
    }

    private int parseMessageId(@NonNull String objectId) {
        String[] objectIdParts = objectId.split("/");
        if (objectIdParts.length < 2) {
            throw new NumberFormatException("Invalid objectId, doesn't contain at least one '/'");
        } else {
            return Integer.parseInt(objectIdParts[1]);
        }
    }

    private void sendNotification(int notificationId, Notification notification) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(notificationId, notification);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // On devices with Android 8.0 (Oreo) or later, notification sound will be handled by the system
            // if notifications have not been disabled by the user.
            return;
        }

        if (!Notification.CATEGORY_CALL.equals(notification.category) || !muteCall) {
            Uri soundUri = NotificationUtils.INSTANCE.getMessageRingtoneUri(context, appPreferences);
            if (soundUri != null && !ApplicationWideCurrentRoomHolder.getInstance().isInCall() &&
                    (DoNotDisturbUtils.INSTANCE.shouldPlaySound() || importantConversation)) {
                AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder().setContentType
                        (AudioAttributes.CONTENT_TYPE_SONIFICATION);

                if (CHAT.equals(decryptedPushMessage.getType()) || ROOM.equals(decryptedPushMessage.getType())) {
                    audioAttributesBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT);
                } else {
                    audioAttributesBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST);
                }

                MediaPlayer mediaPlayer = new MediaPlayer();
                try {
                    mediaPlayer.setDataSource(context, soundUri);
                    mediaPlayer.setAudioAttributes(audioAttributesBuilder.build());

                    mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());
                    mediaPlayer.setOnCompletionListener(MediaPlayer::release);

                    mediaPlayer.prepareAsync();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to set data source");
                }
            }
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        context = getApplicationContext();
        Data data = getInputData();
        String subject = data.getString(BundleKeys.INSTANCE.getKEY_NOTIFICATION_SUBJECT());
        String signature = data.getString(BundleKeys.INSTANCE.getKEY_NOTIFICATION_SIGNATURE());

        try {
            byte[] base64DecodedSubject = Base64.decode(subject, Base64.DEFAULT);
            byte[] base64DecodedSignature = Base64.decode(signature, Base64.DEFAULT);
            PushUtils pushUtils = new PushUtils();
            PrivateKey privateKey = (PrivateKey) pushUtils.readKeyFromFile(false);

            try {
                signatureVerification = pushUtils.verifySignature(base64DecodedSignature,
                        base64DecodedSubject);

                if (signatureVerification.getSignatureValid()) {
                    Cipher cipher = Cipher.getInstance("RSA/None/PKCS1Padding");
                    cipher.init(Cipher.DECRYPT_MODE, privateKey);
                    byte[] decryptedSubject = cipher.doFinal(base64DecodedSubject);
                    decryptedPushMessage = LoganSquare.parse(new String(decryptedSubject),
                            DecryptedPushMessage.class);

                    decryptedPushMessage.setTimestamp(System.currentTimeMillis());
                    if (decryptedPushMessage.getDelete()) {
                        NotificationUtils.INSTANCE.cancelExistingNotificationWithId(
                            context,
                            signatureVerification.getUserEntity(),
                            decryptedPushMessage.getNotificationId());
                    } else if (decryptedPushMessage.getDeleteAll()) {
                        NotificationUtils.INSTANCE.cancelAllNotificationsForAccount(
                            context,
                            signatureVerification.getUserEntity());
                    } else if (decryptedPushMessage.getDeleteMultiple()) {
                        for (long notificationId : decryptedPushMessage.getNotificationIds()) {
                            NotificationUtils.INSTANCE.cancelExistingNotificationWithId(
                                context,
                                signatureVerification.getUserEntity(),
                                notificationId);
                        }
                    } else {
                        credentials = ApiUtils.getCredentials(signatureVerification.getUserEntity().getUsername(),
                                signatureVerification.getUserEntity().getToken());

                        ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(new
                                JavaNetCookieJar(new CookieManager())).build()).build().create(NcApi.class);

                        boolean shouldShowNotification = "spreed".equals(decryptedPushMessage.getApp());

                        if (shouldShowNotification) {
                            Intent intent;
                            Bundle bundle = new Bundle();


                            boolean startACall = "call".equals(decryptedPushMessage.getType());
                            if (startACall) {
                                intent = new Intent(context, CallActivity.class);
                            } else {
                                intent = new Intent(context, MainActivity.class);
                            }

                            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

                            bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), decryptedPushMessage.getId());

                            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(),
                                                 signatureVerification.getUserEntity());

                            bundle.putBoolean(BundleKeys.INSTANCE.getKEY_FROM_NOTIFICATION_START_CALL(),
                                    startACall);

                            intent.putExtras(bundle);

                            switch (decryptedPushMessage.getType()) {
                                case "call":
                                    if (bundle.containsKey(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN())) {
                                        showNotificationForCallWithNoPing(intent);
                                    }
                                    break;
                                case "room":
                                    if (bundle.containsKey(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN())) {
                                        showNotificationWithObjectData(intent);
                                    }
                                    break;
                                case "chat":
                                    if (decryptedPushMessage.getNotificationId() != Long.MIN_VALUE) {
                                        showNotificationWithObjectData(intent);
                                    } else {
                                        showNotification(intent);
                                    }
                                    break;
                                default:
                                    break;
                            }

                        }
                    }
                }
            } catch (NoSuchAlgorithmException e1) {
                Log.d(TAG, "No proper algorithm to decrypt the message " + e1.getLocalizedMessage());
            } catch (NoSuchPaddingException e1) {
                Log.d(TAG, "No proper padding to decrypt the message " + e1.getLocalizedMessage());
            } catch (InvalidKeyException e1) {
                Log.d(TAG, "Invalid private key " + e1.getLocalizedMessage());
            }
        } catch (Exception exception) {
            Log.d(TAG, "Something went very wrong " + exception.getLocalizedMessage());
        }
        return Result.success();
    }
}
