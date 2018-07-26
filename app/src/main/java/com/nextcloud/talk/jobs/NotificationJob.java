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
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.bluelinelabs.logansquare.LoganSquare;
import com.evernote.android.job.Job;
import com.evernote.android.job.util.support.PersistableBundleCompat;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MagicCallActivity;
import com.nextcloud.talk.activities.MainActivity;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.RingtoneSettings;
import com.nextcloud.talk.models.SignatureVerification;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.push.DecryptedPushMessage;
import com.nextcloud.talk.models.json.rooms.Room;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.NotificationUtils;
import com.nextcloud.talk.utils.PushUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.ApplicationWideCurrentRoomHolder;
import com.nextcloud.talk.utils.singletons.ApplicationWideStateHolder;

import org.parceler.Parcels;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.util.Calendar;
import java.util.zip.CRC32;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.inject.Inject;

import autodagger.AutoInjector;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class NotificationJob extends Job {
    public static final String TAG = "NotificationJob";

    @Inject
    UserUtils userUtils;

    @Inject
    AppPreferences appPreferences;

    @Inject
    NcApi ncApi;

    private DecryptedPushMessage decryptedPushMessage;
    private Context context;
    private SignatureVerification signatureVerification;

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        context = getContext();
        PersistableBundleCompat persistableBundleCompat = getParams().getExtras();
        String subject = persistableBundleCompat.getString(BundleKeys.KEY_NOTIFICATION_SUBJECT, "");
        String signature = persistableBundleCompat.getString(BundleKeys.KEY_NOTIFICATION_SIGNATURE, "");

        if (!TextUtils.isEmpty(subject) && !TextUtils.isEmpty(signature)) {

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

                        boolean hasChatSupport = signatureVerification.getUserEntity().hasSpreedCapabilityWithName
                                ("chat-v2");

                        boolean isInTheSameRoomAsNotification = ApplicationWideCurrentRoomHolder.getInstance().
                                getCurrentRoomId().equals(decryptedPushMessage.getId()) &&
                                signatureVerification.getUserEntity().equals(ApplicationWideCurrentRoomHolder
                                        .getInstance().getUserInRoom());

                        boolean shouldShowNotification = decryptedPushMessage.getApp().equals("spreed") &&
                                !decryptedPushMessage.getType().equals("room") &&
                                (!isInTheSameRoomAsNotification ||
                                        !ApplicationWideStateHolder.getInstance().isInForeground() ||
                                        decryptedPushMessage.getType().equals("call"));

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

                            bundle.putParcelable(BundleKeys.KEY_USER_ENTITY, Parcels.wrap(signatureVerification
                                    .getUserEntity()));

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
                                    // do absolutely nothing, we won't even come to this point
                                    break;
                                case "chat":
                                    showNotification(intent);
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
                Log.d(TAG, "Something went very wrong" + exception.getLocalizedMessage());
            }
        }
        return Result.SUCCESS;
    }

    private void showNotificationForCallWithNoPing(Intent intent) {
        UserEntity userEntity = signatureVerification.getUserEntity();
        ncApi.getRoom(ApiUtils.getCredentials(userEntity.getUserId(),
                userEntity.getToken()), ApiUtils.getRoom(userEntity.getBaseUrl(),
                intent.getExtras().getString(BundleKeys.KEY_ROOM_TOKEN)))
                .subscribeOn(Schedulers.newThread())
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {

                    }

                    @Override
                    public void onNext(RoomOverall roomOverall) {
                        Room room = roomOverall.getOcs().getData();

                        if (room.getType().equals(Room.RoomType.ROOM_TYPE_ONE_TO_ONE_CALL)) {
                            context.startActivity(intent);
                        } else {
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

    private void showNotification(Intent intent) {
        int smallIcon;
        Bitmap largeIcon;
        String category;
        int priority = Notification.PRIORITY_HIGH;
        Uri soundUri;

        if (decryptedPushMessage.getType().equals("chat")) {
            smallIcon = R.drawable.ic_chat_white_24dp;
            category = Notification.CATEGORY_MESSAGE;
        } else {
            smallIcon = R.drawable.ic_call_white_24dp;
            category = Notification.CATEGORY_CALL;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(context,
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT);

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        largeIcon = BitmapFactory.decodeResource(context.getResources(), smallIcon);
        CRC32 crc32 = new CRC32();

        Notification.Builder notificationBuilder = new Notification.Builder(context)
                .setLargeIcon(largeIcon)
                .setSmallIcon(smallIcon)
                .setCategory(category)
                .setPriority(priority)
                .setWhen(Calendar.getInstance().getTimeInMillis())
                .setShowWhen(true)
                .setSubText(signatureVerification.getUserEntity().getDisplayName())
                .setContentTitle(decryptedPushMessage.getSubject())
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (Build.VERSION.SDK_INT >= 23) {
            // This method should exist since API 21, but some phones don't have it
            // So as a safeguard, we don't use it until 23
            notificationBuilder.setColor(context.getResources().getColor(R.color.colorPrimary));
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            String groupName = String.format(context.getResources().getString(R.string
                    .nc_notification_channel), signatureVerification.getUserEntity()
                    .getUserId(), signatureVerification.getUserEntity().getBaseUrl());
            crc32.update(groupName.getBytes());

            NotificationUtils.createNotificationChannelGroup(notificationManager,
                    Long.toString(crc32.getValue()),
                    groupName);

            if (decryptedPushMessage.getType().equals("chat")) {
                NotificationUtils.createNotificationChannel(notificationManager,
                        NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES_V3, context.getResources()
                                .getString(R.string.nc_notification_channel_messages), context.getResources()
                                .getString(R.string.nc_notification_channel_messages), true,
                        NotificationManager.IMPORTANCE_HIGH);

                notificationBuilder.setChannelId(NotificationUtils.NOTIFICATION_CHANNEL_MESSAGES_V2);
            } else {
                NotificationUtils.createNotificationChannel(notificationManager,
                        NotificationUtils.NOTIFICATION_CHANNEL_CALLS_V2, context.getResources()
                                .getString(R.string.nc_notification_channel_calls), context.getResources()
                                .getString(R.string.nc_notification_channel_calls_description), true,
                        NotificationManager.IMPORTANCE_HIGH);

                notificationBuilder.setChannelId(NotificationUtils.NOTIFICATION_CHANNEL_CALLS_V2);
            }

            notificationBuilder.setGroup(Long.toString(crc32.getValue()));
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

            if (soundUri != null & !ApplicationWideCurrentRoomHolder.getInstance().isInCall()) {
                MediaPlayer mediaPlayer = MediaPlayer.create(context, soundUri);
                mediaPlayer.start();
                mediaPlayer.setOnCompletionListener(MediaPlayer::release);

            }
        }
    }

}
