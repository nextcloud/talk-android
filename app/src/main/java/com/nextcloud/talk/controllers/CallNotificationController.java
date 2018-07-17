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

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;
import android.support.annotation.NonNull;
import android.support.constraint.ConstraintLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.logansquare.LoganSquare;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.bitmap.TransformationUtils;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.SimpleTarget;
import com.bumptech.glide.request.transition.Transition;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.models.RingtoneSettings;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.models.json.rooms.Room;
import com.nextcloud.talk.models.json.rooms.RoomsOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.MagicFlipView;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.glide.GlideApp;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import org.parceler.Parcels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

@AutoInjector(NextcloudTalkApplication.class)
public class CallNotificationController extends BaseController {

    private static final String TAG = "CallNotificationController";

    @Inject
    NcApi ncApi;

    @Inject
    AppPreferences appPreferences;

    @BindView(R.id.conversationNameTextView)
    TextView conversationNameTextView;

    @BindView(R.id.avatarImageView)
    ImageView avatarImageView;

    @BindView(R.id.callAnswerVoiceOnlyView)
    MagicFlipView callAnswerVoiceOnlyView;

    @BindView(R.id.callAnswerCameraView)
    MagicFlipView callAnswerCameraView;

    @BindView(R.id.constraintLayout)
    ConstraintLayout constraintLayout;

    private List<Disposable> disposablesList = new ArrayList<>();
    private Bundle originalBundle;
    private String roomId;
    private UserEntity userBeingCalled;
    private String credentials;
    private Room currentRoom;
    private MediaPlayer mediaPlayer;
    private boolean leavingScreen = false;
    private RenderScript renderScript;

    public CallNotificationController(Bundle args) {
        super(args);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        this.roomId = args.getString(BundleKeys.KEY_ROOM_ID, "");
        this.userBeingCalled = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_USER_ENTITY));

        this.originalBundle = args;

        credentials = ApiUtils.getCredentials(userBeingCalled.getUserId(), userBeingCalled.getToken());
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_call_notification, container, false);
    }

    private void showAnswerControls() {
        callAnswerCameraView.setVisibility(View.VISIBLE);
        callAnswerVoiceOnlyView.setVisibility(View.VISIBLE);
    }

    @OnClick(R.id.callControlHangupView)
    void hangup() {
        leavingScreen = true;

        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    @OnClick(R.id.callAnswerCameraView)
    void answerWithCamera() {
        originalBundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, false);
        proceedToCall();
    }

    @OnClick(R.id.callAnswerVoiceOnlyView)
    void answerVoiceOnly() {
        originalBundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, true);
        proceedToCall();
    }

    private void proceedToCall() {
        originalBundle.putString(BundleKeys.KEY_ROOM_TOKEN, currentRoom.getToken());

        getRouter().setRoot(RouterTransaction.with(new CallController(originalBundle))
                .popChangeHandler(new HorizontalChangeHandler())
                .pushChangeHandler(new HorizontalChangeHandler()));
    }

    private void checkIfAnyParticipantsRemainInRoom() {
        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForParticipants(userBeingCalled.getBaseUrl(),
                currentRoom.getToken()))
                .subscribeOn(Schedulers.newThread())
                .takeWhile(observable -> !leavingScreen)
                .retry(3)
                .subscribe(new Observer<ParticipantsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposablesList.add(d);
                    }

                    @Override
                    public void onNext(ParticipantsOverall participantsOverall) {
                        boolean hasParticipantsInCall = false;
                        boolean inCallOnDifferentDevice = false;
                        List<Participant> participantList = participantsOverall.getOcs().getData();
                        for (Participant participant : participantList) {
                            if (participant.isInCall()) {
                                hasParticipantsInCall = true;

                                if (participant.getUserId().equals(userBeingCalled.getUserId())) {
                                    inCallOnDifferentDevice = true;
                                    break;
                                }
                            }
                        }

                        if (!hasParticipantsInCall || inCallOnDifferentDevice) {
                            if (getActivity() != null) {
                                getActivity().runOnUiThread(() -> hangup());
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                        if (!leavingScreen) {
                            checkIfAnyParticipantsRemainInRoom();
                        }
                    }
                });

    }

    private void handleFromNotification() {
        ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(userBeingCalled.getBaseUrl()))
                .subscribeOn(Schedulers.newThread())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposablesList.add(d);
                    }

                    @Override
                    public void onNext(RoomsOverall roomsOverall) {
                        for (Room room : roomsOverall.getOcs().getData()) {
                            if (roomId.equals(room.getRoomId())) {
                                currentRoom = room;
                                if (conversationNameTextView != null) {
                                    conversationNameTextView.setText(currentRoom.getDisplayName());
                                    loadAvatar();
                                    checkIfAnyParticipantsRemainInRoom();
                                    showAnswerControls();
                                }
                                break;
                            }
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

    @SuppressLint("LongLogTag")
    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);

        renderScript = RenderScript.create(getActivity());
        handleFromNotification();

        String callRingtonePreferenceString = appPreferences.getCallRingtoneUri();
        Uri ringtoneUri;

        if (TextUtils.isEmpty(callRingtonePreferenceString)) {
            // play default sound
            ringtoneUri = Uri.parse("android.resource://" + getApplicationContext().getPackageName()+
                    "/raw/librem_by_feandesign_call");
        } else {
            try {
                RingtoneSettings ringtoneSettings = LoganSquare.parse(callRingtonePreferenceString, RingtoneSettings.class);
                ringtoneUri = ringtoneSettings.getRingtoneUri();
            } catch (IOException e) {
                Log.e(TAG, "Failed to parse ringtone settings");
                ringtoneUri = Uri.parse("android.resource://" + getApplicationContext().getPackageName()+
                        "/raw/librem_by_feandesign_call");
            }
        }

        if (ringtoneUri != null) {
            mediaPlayer = MediaPlayer.create(getApplicationContext(), ringtoneUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }
    }

    private void loadAvatar() {
        int avatarSize = Math.round(NextcloudTalkApplication
                .getSharedApplication().getResources().getDimension(R.dimen.avatar_fetching_size_very_big));

        switch (currentRoom.getType()) {
            case ROOM_TYPE_ONE_TO_ONE_CALL:
                avatarImageView.setVisibility(View.VISIBLE);

                GlideUrl glideUrl = new GlideUrl(ApiUtils.getUrlForAvatarWithName(userBeingCalled.getBaseUrl(),
                        currentRoom.getName(), R.dimen.avatar_size_very_big), new LazyHeaders.Builder()
                        .setHeader("Accept", "image/*")
                        .setHeader("User-Agent", ApiUtils.getUserAgent())
                        .build());

                GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .load(glideUrl)
                        .centerInside()
                        .override(avatarSize, avatarSize)
                        .into(new SimpleTarget<Bitmap>() {
                            @Override
                            public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                                if (getActivity() != null) {
                                    avatarImageView.setImageBitmap(TransformationUtils.circleCrop(GlideApp.get
                                            (getActivity()).getBitmapPool(), resource, avatarSize, avatarSize));
                                }

                                final Allocation input = Allocation.createFromBitmap(renderScript, resource);
                                final Allocation output = Allocation.createTyped(renderScript, input.getType());
                                final ScriptIntrinsicBlur script = ScriptIntrinsicBlur.create(renderScript, Element
                                        .U8_4(renderScript));
                                script.setRadius(15f);
                                script.setInput(input);
                                script.forEach(output);
                                output.copyTo(resource);

                                constraintLayout.setBackground(new BitmapDrawable(resource));
                            }
                        });


                break;
            case ROOM_GROUP_CALL:
                GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .load(R.drawable.ic_group_white_24px)
                        .centerInside()
                        .override(avatarSize, avatarSize)
                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                        .into(avatarImageView);
            case ROOM_PUBLIC_CALL:
                GlideApp.with(NextcloudTalkApplication.getSharedApplication().getApplicationContext())
                        .asBitmap()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .load(R.drawable.ic_link_white_24px)
                        .centerInside()
                        .override(avatarSize, avatarSize)
                        .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                        .into(avatarImageView);
                break;
            default:
        }
    }

    private void endMediaPlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        leavingScreen = true;
        dispose();
        endMediaPlayer();
        super.onDestroy();
    }

    private void dispose() {
        Disposable disposable;
        for (int i = 0; i < disposablesList.size(); i++) {
            if ((disposable = disposablesList.get(i)).isDisposed()) {
                disposable.dispose();
            }
        }
    }
}