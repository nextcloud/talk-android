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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import autodagger.AutoInjector;
import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.activities.MainActivity;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.RingtoneSettings;
import com.nextcloud.talk.models.SignatureVerification;
import com.nextcloud.talk.models.database.ArbitraryStorageEntity;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.chat.ChatUtils;
import com.nextcloud.talk.models.json.notifications.NotificationOverall;
import com.nextcloud.talk.models.json.push.DecryptedPushMessage;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DoNotDisturbUtils;
import com.nextcloud.talk.utils.NotificationUtils;
import com.nextcloud.talk.utils.PushUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import org.parceler.Parcels;
import retrofit2.Retrofit;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;
import java.io.IOException;
import java.net.CookieManager;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Calendar;
import java.util.HashMap;
import java.util.zip.CRC32;

@AutoInjector(NextcloudTalkApplication.class)
public class NotificationWorker extends Worker {
    public static final String TAG = "NotificationWorker";

    @Inject
    AppPreferences appPreferences;

    @Inject
    ArbitraryStorageUtils arbitraryStorageUtils;

    @Inject
    Retrofit retrofit;

    @Inject
    OkHttpClient okHttpClient;

    NcApi ncApi;

    private DecryptedPushMessage decryptedPushMessage;
    private Context context;
    private SignatureVerification signatureVerification;
    private String conversationType = "";

    private String credentials;

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    private void showNotificationForCallWithNoPing(Intent intent) {
        UserEntity userEntity = signatureVerification.getUserEntity();

        ArbitraryStorageEntity arbitraryStorageEntity;
        boolean muteCalls = false;

        if ((arbitraryStorageEntity = arbitraryStorageUtils.getStorageSetting(userEntity.getId(),
                "mute_calls", intent.getExtras().getString(BundleKeys.KEY_ROOM_TOKEN))) != null) {
            muteCalls = Boolean.parseBoolean(arbitraryStorageEntity.getValue());
        }

        if (!muteCalls) {
            ncApi.getRoom(credentials, ApiUtils.getRoom(userEntity.getBaseUrl(),
                    intent.getExtras().getString(BundleKeys.KEY_ROOM_TOKEN)))
                    .blockingSubscribe(new Observer<RoomOverall>() {
                        @Override
                        public void onSubscribe(Disposable d) {

                        }

                        @Override
                        public void onNext(RoomOverall roomOverall) {
                            Conversation conversation = roomOverall.getOcs().getData();

                            intent.putExtra(BundleKeys.KEY_ROOM, Parcels.wrap(conversation));
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
                                showNotification(intent);
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
    }

    private void showMessageNotificationWithObjectData(Intent intent) {
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
                            decryptedPushMessage.setText(ChatUtils.getParsedMessage(notification.getMessageRich(),
                                    notification.getMessageRichParameters()));
                        } else {
                            decryptedPushMessage.setText(notification.getMessage());
                        }

                        HashMap<String, HashMap<String, String>> subjectRichParameters = notification
                                .getSubjectRichParameters();

                        if (subjectRichParameters != null && subjectRichParameters
                                .size() > 0 && subjectRichParameters.containsKey("call")
                                && subjectRichParameters.containsKey("user")) {
                            HashMap<String, String> callHashMap = subjectRichParameters.get("call");
                            HashMap<String, String> userHashMap = subjectRichParameters.get("user");

                            if (callHashMap != null && callHashMap.size() > 0 && callHashMap.containsKey("call-type")) {
                                conversationType = callHashMap.get("call-type");

                                if ("one2one".equals(conversationType)) {
                                    decryptedPushMessage.setSubject(userHashMap.get("name"));
                                }
                            }
                        }

                        showNotification(intent);
                    }

                    @Override
                    public void onError(Throwable e) {
                        showNotification(intent);
                    }

                    @Override
                    public void onComplete() {
                    }
                });
    }

    private void showNotification(Intent intent) {
        int smallIcon;
        Bitmap largeIcon = null;
        String category;
        int priority = Notification.PRIORITY_HIGH;
        Uri soundUri;

        smallIcon = R.drawable.ic_logo;

        if (decryptedPushMessage.getType().equals("chat") || decryptedPushMessage.getType().equals("room")) {
            category = Notification.CATEGORY_MESSAGE;
        } else {
            category = Notification.CATEGORY_CALL;
        }

        switch (conversationType) {
            case "one2one":
                if (decryptedPushMessage.getType().equals("chat") || decryptedPushMessage.getType().equals("room")) {
                    largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_chat_black_24dp);
                } else {
                    largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_call_black_24dp);
                }
                break;
            case "group":
                largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_people_group_black_24px);
                break;
            case "public":
                largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_link_black_24px);
                break;
            default:
                if (decryptedPushMessage.getType().equals("chat") || decryptedPushMessage.getType().equals("room")) {
                    largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_chat_black_24dp);
                } else {
                    largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_call_black_24dp);
                }
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);


        CRC32 crc32 = new CRC32();

        Uri uri = Uri.parse(signatureVerification.getUserEntity().getBaseUrl());
        String baseUrl = uri.getHost();

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, "1")
                .setLargeIcon(largeIcon)
                .setSmallIcon(smallIcon)
                .setCategory(category)
                .setPriority(priority)
                .setWhen(Calendar.getInstance().getTimeInMillis())
                .setShowWhen(true)
                .setSubText(baseUrl)
                .setContentTitle(decryptedPushMessage.getSubject())
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (!TextUtils.isEmpty(decryptedPushMessage.getText())) {
            notificationBuilder.setContentText(decryptedPushMessage.getText());
        }

        if (Build.VERSION.SDK_INT >= 23) {
            // This method should exist since API 21, but some phones don't have it
            // So as a safeguard, we don't use it until 23
            notificationBuilder.setColor(context.getResources().getColor(R.color.colorPrimary));
        }

        String groupName = signatureVerification.getUserEntity().getId() + "@" + decryptedPushMessage.getId();
        crc32.update(groupName.getBytes());
        notificationBuilder.setGroup(Long.toString(crc32.getValue()));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            /*NotificationUtils.createNotificationChannelGroup(context,
                    Long.toString(crc32.getValue()),
                    groupName);*/

            if (decryptedPushMessage.getType().equals("chat") || decryptedPushMessage.getType().equals("room")) {
                NotificationUtils.createNotificationChannel(context,
                        NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES_V3, context.getResources()
                                .getString(R.string.nc_notification_channel_messages), context.getResources()
                                .getString(R.string.nc_notification_channel_messages), true,
                        NotificationManager.IMPORTANCE_HIGH);

                notificationBuilder.setChannelId(NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES_V3);
            } else {
                NotificationUtils.createNotificationChannel(context,
                        NotificationUtils.NOTIFICATION_CHANNEL_CALLS_V3, context.getResources()
                                .getString(R.string.nc_notification_channel_calls), context.getResources()
                                .getString(R.string.nc_notification_channel_calls_description), true,
                        NotificationManager.IMPORTANCE_HIGH);

                notificationBuilder.setChannelId(NotificationUtils.NOTIFICATION_CHANNEL_CALLS_V3);
            }

        } else {
            // red color for the lights
            notificationBuilder.setLights(0xFFFF0000, 200, 200);
        }

        notificationBuilder.setContentIntent(pendingIntent);

        String stringForCrc = decryptedPushMessage.getSubject() + " " + signatureVerification
                .getUserEntity().getDisplayName() + " " + signatureVerification.getUserEntity
                ().getBaseUrl() + System.currentTimeMillis();

        crc32 = new CRC32();
        crc32.update(stringForCrc.getBytes());

        String ringtonePreferencesString;
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


        if (notificationManager != null) {
            notificationManager.notify((int) crc32.getValue(), notificationBuilder.build());

            if (soundUri != null & !ApplicationWideCurrentRoomHolder.getInstance().isInCall() &&
                    DoNotDisturbUtils.shouldPlaySound()) {
                AudioAttributes.Builder audioAttributesBuilder = new AudioAttributes.Builder().setContentType
                        (AudioAttributes.CONTENT_TYPE_SONIFICATION);

                if (decryptedPushMessage.getType().equals("chat") || decryptedPushMessage.getType().equals("room")) {
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


            if (DoNotDisturbUtils.shouldVibrate(appPreferences.getShouldVibrateSetting())) {
                Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

                if (vibrator != null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(500);
                    }
                }
            }
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        context = getApplicationContext();
        Data data = getInputData();
        String subject = data.getString(BundleKeys.KEY_NOTIFICATION_SUBJECT);
        String signature = data.getString(BundleKeys.KEY_NOTIFICATION_SIGNATURE);

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

                    credentials = ApiUtils.getCredentials(signatureVerification.getUserEntity().getUsername(),
                            signatureVerification.getUserEntity().getToken());

                    ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(new
                            JavaNetCookieJar(new CookieManager())).build()).build().create(NcApi.class);

                    boolean hasChatSupport = signatureVerification.getUserEntity().
                            hasSpreedCapabilityWithName("chat-v2");

                    boolean shouldShowNotification = decryptedPushMessage.getApp().equals("spreed");

                    if (shouldShowNotification) {
                        Intent intent;
                        Bundle bundle = new Bundle();


                        boolean startACall = decryptedPushMessage.getType().equals("call") || !hasChatSupport;
                        if (startACall) {
                            intent = new Intent(context, MagicCallActivity.class);
                        } else {
                            intent = new Intent(context, MainActivity.class);
                        }

                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);


                        if (!signatureVerification.getUserEntity().hasSpreedCapabilityWithName
                                ("no-ping")) {
                            bundle.putString(BundleKeys.KEY_ROOM_ID, decryptedPushMessage.getId());
                        } else {
                            bundle.putString(BundleKeys.KEY_ROOM_TOKEN, decryptedPushMessage.getId());
                        }

                        bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, signatureVerification.getUserEntity());

                        bundle.putBoolean(BundleKeys.KEY_FROM_NOTIFICATION_START_CALL,
                                startACall);

                        intent.putExtras(bundle);

                        switch (decryptedPushMessage.getType()) {
                            case "call":
                                if (!bundle.containsKey(BundleKeys.KEY_ROOM_TOKEN)) {
                                    context.startActivity(intent);
                                } else {
                                    showNotificationForCallWithNoPing(intent);
                                }
                                break;
                            case "room":
                                if (bundle.containsKey(BundleKeys.KEY_ROOM_TOKEN)) {
                                    showNotificationForCallWithNoPing(intent);
                                }
                                break;
                            case "chat":
                                if (decryptedPushMessage.getNotificationId() != Long.MIN_VALUE) {
                                    showMessageNotificationWithObjectData(intent);
                                } else {
                                    showNotification(intent);
                                }
                                break;
                            default:
                                break;
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
