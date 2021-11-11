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
import android.app.NotificationManager;
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
import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.postprocessors.RoundAsCirclePostprocessor;
import com.facebook.imagepipeline.request.ImageRequest;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.CallActivity;
import com.nextcloud.talk.activities.MainActivity;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.RingtoneSettings;
import com.nextcloud.talk.models.SignatureVerification;
import com.nextcloud.talk.models.database.ArbitraryStorageEntity;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatUtils;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.notifications.NotificationOverall;
import com.nextcloud.talk.models.json.push.DecryptedPushMessage;
import com.nextcloud.talk.models.json.push.NotificationUser;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
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
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;
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

        if ((arbitraryStorageEntity = arbitraryStorageUtils.getStorageSetting(userEntity.getId(),
                "important_conversation", intent.getExtras().getString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN()))) != null) {
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

                        muteCall = !(conversation.notificationCalls == 1);
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
                            decryptedPushMessage.setText(ChatUtils.Companion.getParsedMessage(notification.getMessageRich(),
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
                                if (notification.getObjectType().equals("chat")) {
                                    decryptedPushMessage.setSubject(callHashMap.get("name"));
                                } else {
                                    decryptedPushMessage.setSubject(notification.getSubject());
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

        intent.setAction(Long.toString(System.currentTimeMillis()));

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                0, intent, 0);

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
                .setContentTitle(EmojiCompat.get().process(decryptedPushMessage.getSubject()))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (!TextUtils.isEmpty(decryptedPushMessage.getText())) {
            notificationBuilder.setContentText(EmojiCompat.get().process(decryptedPushMessage.getText()));
        }

        if (Build.VERSION.SDK_INT >= 23) {
            // This method should exist since API 21, but some phones don't have it
            // So as a safeguard, we don't use it until 23
            notificationBuilder.setColor(context.getResources().getColor(R.color.colorPrimary));
        }

        Bundle notificationInfo = new Bundle();
        notificationInfo.putLong(BundleKeys.INSTANCE.getKEY_INTERNAL_USER_ID(), signatureVerification.getUserEntity().getId());
        // could be an ID or a TOKEN
        notificationInfo.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), decryptedPushMessage.getId());
        notificationInfo.putLong(BundleKeys.INSTANCE.getKEY_NOTIFICATION_ID(), decryptedPushMessage.getNotificationId());
        notificationBuilder.setExtras(notificationInfo);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            /*NotificationUtils.createNotificationChannelGroup(context,
                    Long.toString(crc32.getValue()),
                    groupName);*/

            if (CHAT.equals(decryptedPushMessage.getType()) || ROOM.equals(decryptedPushMessage.getType())) {
                AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder().setContentType
                        (AudioAttributes.CONTENT_TYPE_SONIFICATION);
                audioAttributesBuilder.setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT);

                String ringtonePreferencesString;
                Uri soundUri;

                ringtonePreferencesString = appPreferences.getMessageRingtoneUri();
                if (TextUtils.isEmpty(ringtonePreferencesString)) {
                    soundUri = Uri.parse("android.resource://" + context.getPackageName() +
                            "/raw/librem_by_feandesign_message");
                } else {
                    try {
                        RingtoneSettings ringtoneSettings = LoganSquare.parse
                                (ringtonePreferencesString, RingtoneSettings.class);
                        soundUri = ringtoneSettings.getRingtoneUri();
                    } catch (IOException exception) {
                        soundUri = Uri.parse("android.resource://" + context.getPackageName() +
                                "/raw/librem_by_feandesign_message");
                    }
                }

                NotificationUtils.INSTANCE.createNotificationChannel(context,
                        NotificationUtils.INSTANCE.getNOTIFICATION_CHANNEL_MESSAGES_V3(), context.getResources()
                                .getString(R.string.nc_notification_channel_messages), context.getResources()
                                .getString(R.string.nc_notification_channel_messages), true,
                        NotificationManager.IMPORTANCE_HIGH, soundUri, audioAttributesBuilder.build(), null, false);

                notificationBuilder.setChannelId(NotificationUtils.INSTANCE.getNOTIFICATION_CHANNEL_MESSAGES_V3());
            } else {
                /*NotificationUtils.INSTANCE.createNotificationChannel(context,
                        NotificationUtils.INSTANCE.getNOTIFICATION_CHANNEL_CALLS_V3(), context.getResources()
                                .getString(R.string.nc_notification_channel_calls), context.getResources()
                                .getString(R.string.nc_notification_channel_calls_description), true,
                        NotificationManager.IMPORTANCE_HIGH);

                notificationBuilder.setChannelId(NotificationUtils.INSTANCE.getNOTIFICATION_CHANNEL_CALLS_V3());*/
            }

        } else {
            // red color for the lights
            notificationBuilder.setLights(0xFFFF0000, 200, 200);
        }

        notificationBuilder.setContentIntent(pendingIntent);


        CRC32 crc32 = new CRC32();

        String groupName = signatureVerification.getUserEntity().getId() + "@" + decryptedPushMessage.getId();
        crc32.update(groupName.getBytes());
        notificationBuilder.setGroup(Long.toString(crc32.getValue()));

        // notificationId
        crc32 = new CRC32();
        String stringForCrc = String.valueOf(System.currentTimeMillis());
        crc32.update(stringForCrc.getBytes());

        StatusBarNotification activeStatusBarNotification =
                NotificationUtils.INSTANCE.findNotificationForRoom(context,
                        signatureVerification.getUserEntity(), decryptedPushMessage.getId());

        int notificationId;

        if (activeStatusBarNotification != null) {
            notificationId = activeStatusBarNotification.getId();
        } else {
            notificationId = (int) crc32.getValue();
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && decryptedPushMessage.getNotificationUser() != null && decryptedPushMessage.getType().equals("chat")) {
            NotificationCompat.MessagingStyle style = null;
            if (activeStatusBarNotification != null) {
                Notification activeNotification = activeStatusBarNotification.getNotification();
                style = NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(activeNotification);
            }

            Person.Builder person =
                    new Person.Builder().setKey(signatureVerification.getUserEntity().getId() +
                            "@" + decryptedPushMessage.getNotificationUser().getId()).setName(EmojiCompat.get().process(decryptedPushMessage.getNotificationUser().getName())).setBot(decryptedPushMessage.getNotificationUser().getType().equals("bot"));

            notificationBuilder.setOnlyAlertOnce(true);

            if (decryptedPushMessage.getNotificationUser().getType().equals("user") || decryptedPushMessage.getNotificationUser().getType().equals("guest")) {
                String avatarUrl = ApiUtils.getUrlForAvatarWithName(signatureVerification.getUserEntity().getBaseUrl(), decryptedPushMessage.getNotificationUser().getId(), R.dimen.avatar_size);

                if (decryptedPushMessage.getNotificationUser().getType().equals("guest")) {
                    avatarUrl = ApiUtils.getUrlForAvatarWithNameForGuests(signatureVerification.getUserEntity().getBaseUrl(),
                            decryptedPushMessage.getNotificationUser().getName(), R.dimen.avatar_size);
                }

                ImageRequest imageRequest =
                        DisplayUtils.getImageRequestForUrl(avatarUrl, null);
                ImagePipeline imagePipeline = Fresco.getImagePipeline();
                DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, context);

                NotificationCompat.MessagingStyle finalStyle = style;
                dataSource.subscribe(
                        new BaseBitmapDataSubscriber() {
                            @Override
                            protected void onNewResultImpl(Bitmap bitmap) {
                                if (bitmap != null) {
                                    new RoundAsCirclePostprocessor(true).process(bitmap);
                                    person.setIcon(IconCompat.createWithBitmap(bitmap));
                                    notificationBuilder.setStyle(getStyle(person.build(),
                                            finalStyle));
                                    sendNotificationWithId(notificationId, notificationBuilder.build());

                                }
                            }

                            @Override
                            protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                                notificationBuilder.setStyle(getStyle(person.build(), finalStyle));
                                sendNotificationWithId(notificationId, notificationBuilder.build());
                            }
                        },
                        UiThreadImmediateExecutorService.getInstance());
            } else {
                notificationBuilder.setStyle(getStyle(person.build(), style));
                sendNotificationWithId(notificationId, notificationBuilder.build());
            }
        } else {
            sendNotificationWithId(notificationId, notificationBuilder.build());
        }

    }

    private NotificationCompat.MessagingStyle getStyle(Person person, @Nullable NotificationCompat.MessagingStyle style) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NotificationCompat.MessagingStyle newStyle =
                    new NotificationCompat.MessagingStyle(person);

            newStyle.setConversationTitle(decryptedPushMessage.getSubject());
            newStyle.setGroupConversation(!conversationType.equals("one2one"));

            if (style != null) {
                style.getMessages().forEach(message -> newStyle.addMessage(new NotificationCompat.MessagingStyle.Message(message.getText(), message.getTimestamp(), message.getPerson())));
            }

            newStyle.addMessage(decryptedPushMessage.getText(), decryptedPushMessage.getTimestamp(), person);
            return newStyle;
        }

        // we'll never come here
        return style;
    }

    private void sendNotificationWithId(int notificationId, Notification notification) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(notificationId, notification);


        if (!notification.category.equals(Notification.CATEGORY_CALL) || !muteCall) {
            String ringtonePreferencesString;
            Uri soundUri;

            ringtonePreferencesString = appPreferences.getMessageRingtoneUri();
            if (TextUtils.isEmpty(ringtonePreferencesString)) {
                soundUri = Uri.parse("android.resource://" + context.getPackageName() +
                        "/raw/librem_by_feandesign_message");
            } else {
                try {
                    RingtoneSettings ringtoneSettings = LoganSquare.parse
                            (ringtonePreferencesString, RingtoneSettings.class);
                    soundUri = ringtoneSettings.getRingtoneUri();
                } catch (IOException exception) {
                    soundUri = Uri.parse("android.resource://" + context.getPackageName() +
                            "/raw/librem_by_feandesign_message");
                }
            }

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

                if (signatureVerification.isSignatureValid()) {
                    Cipher cipher = Cipher.getInstance("RSA/None/PKCS1Padding");
                    cipher.init(Cipher.DECRYPT_MODE, privateKey);
                    byte[] decryptedSubject = cipher.doFinal(base64DecodedSubject);
                    decryptedPushMessage = LoganSquare.parse(new String(decryptedSubject),
                            DecryptedPushMessage.class);

                    decryptedPushMessage.setTimestamp(System.currentTimeMillis());
                    if (decryptedPushMessage.isDelete()) {
                        NotificationUtils.INSTANCE.cancelExistingNotificationWithId(context, signatureVerification.getUserEntity(), decryptedPushMessage.getNotificationId());
                    } else if (decryptedPushMessage.isDeleteAll()) {
                        NotificationUtils.INSTANCE.cancelAllNotificationsForAccount(context, signatureVerification.getUserEntity());
                    } else if (decryptedPushMessage.isDeleteMultiple()) {
                        for (long notificationId : decryptedPushMessage.getNotificationIds()) {
                            NotificationUtils.INSTANCE.cancelExistingNotificationWithId(context, signatureVerification.getUserEntity(), notificationId);
                        }
                    } else {
                        credentials = ApiUtils.getCredentials(signatureVerification.getUserEntity().getUsername(),
                                signatureVerification.getUserEntity().getToken());

                        ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(new
                                JavaNetCookieJar(new CookieManager())).build()).build().create(NcApi.class);

                        boolean shouldShowNotification = decryptedPushMessage.getApp().equals("spreed");

                        if (shouldShowNotification) {
                            Intent intent;
                            Bundle bundle = new Bundle();


                            boolean startACall = decryptedPushMessage.getType().equals("call");
                            if (startACall) {
                                intent = new Intent(context, CallActivity.class);
                            } else {
                                intent = new Intent(context, MainActivity.class);
                            }

                            intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

                            bundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), decryptedPushMessage.getId());

                            bundle.putParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY(), signatureVerification.getUserEntity());

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
