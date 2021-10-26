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
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.renderscript.RenderScript;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler;
import com.bluelinelabs.logansquare.LoganSquare;
import com.facebook.common.executors.UiThreadImmediateExecutorService;
import com.facebook.common.references.CloseableReference;
import com.facebook.datasource.DataSource;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.view.SimpleDraweeView;
import com.facebook.imagepipeline.core.ImagePipeline;
import com.facebook.imagepipeline.datasource.BaseBitmapDataSubscriber;
import com.facebook.imagepipeline.image.CloseableImage;
import com.facebook.imagepipeline.postprocessors.BlurPostProcessor;
import com.facebook.imagepipeline.request.ImageRequest;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.CallNotificationClick;
import com.nextcloud.talk.events.ConfigurationChangeEvent;
import com.nextcloud.talk.models.RingtoneSettings;
import com.nextcloud.talk.models.database.CapabilitiesUtil;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.conversations.Conversation;
import com.nextcloud.talk.models.json.conversations.RoomOverall;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.DoNotDisturbUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.AvatarStatusCodeHolder;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.michaelevans.colorart.library.ColorArt;
import org.parceler.Parcels;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;

@AutoInjector(NextcloudTalkApplication.class)
public class CallNotificationController extends BaseController {

    private static final String TAG = "CallNotificationController";

    @Inject
    NcApi ncApi;

    @Inject
    AppPreferences appPreferences;

    @Inject
    Cache cache;

    @Inject
    EventBus eventBus;

    @Inject
    Context context;

    @BindView(R.id.incomingCallVoiceOrVideoTextView)
    TextView incomingCallVoiceOrVideoTextView;

    @BindView(R.id.conversationNameTextView)
    TextView conversationNameTextView;

    @BindView(R.id.avatarImageView)
    SimpleDraweeView avatarImageView;

    @BindView(R.id.callAnswerVoiceOnlyView)
    SimpleDraweeView callAnswerVoiceOnlyView;

    @BindView(R.id.callAnswerCameraView)
    SimpleDraweeView callAnswerCameraView;

    @BindView(R.id.backgroundImageView)
    ImageView backgroundImageView;

    @BindView(R.id.incomingTextRelativeLayout)
    RelativeLayout incomingTextRelativeLayout;

    private List<Disposable> disposablesList = new ArrayList<>();
    private Bundle originalBundle;
    private String roomId;
    private UserEntity userBeingCalled;
    private String credentials;
    private Conversation currentConversation;
    private MediaPlayer mediaPlayer;
    private boolean leavingScreen = false;
    private RenderScript renderScript;
    private Handler handler;

    public CallNotificationController(Bundle args) {
        super(args);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);

        eventBus.post(new CallNotificationClick());
        this.roomId = args.getString(BundleKeys.INSTANCE.getKEY_ROOM_ID(), "");
        this.currentConversation = Parcels.unwrap(args.getParcelable(BundleKeys.INSTANCE.getKEY_ROOM()));
        this.userBeingCalled = args.getParcelable(BundleKeys.INSTANCE.getKEY_USER_ENTITY());

        this.originalBundle = args;
        credentials = ApiUtils.getCredentials(userBeingCalled.getUsername(), userBeingCalled.getToken());
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
        originalBundle.putBoolean(BundleKeys.INSTANCE.getKEY_CALL_VOICE_ONLY(), false);
        proceedToCall();
    }

    @OnClick(R.id.callAnswerVoiceOnlyView)
    void answerVoiceOnly() {
        originalBundle.putBoolean(BundleKeys.INSTANCE.getKEY_CALL_VOICE_ONLY(), true);
        proceedToCall();
    }

    private void proceedToCall() {
        originalBundle.putString(BundleKeys.INSTANCE.getKEY_ROOM_TOKEN(), currentConversation.getToken());
        originalBundle.putString(BundleKeys.INSTANCE.getKEY_CONVERSATION_NAME(), currentConversation.getDisplayName());

        getRouter().replaceTopController(RouterTransaction.with(new CallController(originalBundle))
                                                 .popChangeHandler(new HorizontalChangeHandler())
                                                 .pushChangeHandler(new HorizontalChangeHandler())
                                                 .tag(CallController.TAG));
    }

    private void checkIfAnyParticipantsRemainInRoom() {
        int apiVersion = ApiUtils.getCallApiVersion(userBeingCalled, new int[] {ApiUtils.APIv4, 1});

        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForCall(apiVersion, userBeingCalled.getBaseUrl(),
                                                                  currentConversation.getToken()))
                .subscribeOn(Schedulers.io())
                .takeWhile(observable -> !leavingScreen)
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
                        hasParticipantsInCall = participantList.size() > 0;

                        if (hasParticipantsInCall) {
                            for (Participant participant : participantList) {
                                if (participant.getActorType() == Participant.ActorType.USERS &&
                                        participant.getActorId().equals(userBeingCalled.getUserId())) {
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
                            handler.postDelayed(() -> checkIfAnyParticipantsRemainInRoom(), 5000);
                        }
                    }
                });

    }

    private void handleFromNotification() {
        int apiVersion = ApiUtils.getConversationApiVersion(userBeingCalled, new int[] {ApiUtils.APIv4,
                ApiUtils.APIv3, 1});

        ncApi.getRoom(credentials, ApiUtils.getUrlForRoom(apiVersion, userBeingCalled.getBaseUrl(), roomId))
                .subscribeOn(Schedulers.io())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomOverall>() {
                    @Override
                    public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                        disposablesList.add(d);
                    }

                    @Override
                    public void onNext(@io.reactivex.annotations.NonNull RoomOverall roomOverall) {
                        currentConversation = roomOverall.getOcs().data;
                        runAllThings();

                        if (apiVersion >= 3) {
                            boolean hasCallFlags =
                                    CapabilitiesUtil.hasSpreedFeatureCapability(userBeingCalled,
                                                                                "conversation-call-flags");
                            if (hasCallFlags) {
                                if (isInCallWithVideo(currentConversation.callFlag)) {
                                    incomingCallVoiceOrVideoTextView.setText(
                                            String.format(getResources().getString(R.string.nc_call_video),
                                                          getResources().getString(R.string.nc_app_product_name)));
                                } else {
                                    incomingCallVoiceOrVideoTextView.setText(
                                            String.format(getResources().getString(R.string.nc_call_voice),
                                                          getResources().getString(R.string.nc_app_product_name)));
                                }
                            }
                        }
                    }

                    @SuppressLint("LongLogTag")
                    @Override
                    public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                        Log.e(TAG, e.getMessage(), e);
                    }

                    @Override
                    public void onComplete() {

                    }
                });
    }

    private boolean isInCallWithVideo(int callFlag) {
        return (Participant.ParticipantFlags.IN_CALL_WITH_VIDEO.getValue() == callFlag
                || Participant.ParticipantFlags.IN_CALL_WITH_AUDIO_AND_VIDEO.getValue() == callFlag);
    }

    private void runAllThings() {
        if (conversationNameTextView != null) {
            conversationNameTextView.setText(currentConversation.getDisplayName());
        }

        // TODO: load avatar, but don't block UI!
//        loadAvatar();
        checkIfAnyParticipantsRemainInRoom();
        showAnswerControls();
    }

    @SuppressLint({"LongLogTag"})
    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);

        String callDescriptionWithoutTypeInfo =
                String.format(
                        getResources().getString(R.string.nc_call_unknown),
                        getResources().getString(R.string.nc_app_product_name));

        incomingCallVoiceOrVideoTextView.setText(callDescriptionWithoutTypeInfo);

        renderScript = RenderScript.create(getActivity());

        if (handler == null) {
            handler = new Handler();

            try {
                cache.evictAll();
            } catch (IOException e) {
                Log.e(TAG, "Failed to evict cache");
            }
        }

        if (currentConversation == null) {
            handleFromNotification();
        } else {
            runAllThings();
        }

        if (DoNotDisturbUtils.INSTANCE.shouldPlaySound()) {
            playRingtoneSound();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(ConfigurationChangeEvent configurationChangeEvent) {
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) avatarImageView.getLayoutParams();
        int dimen = (int) getResources().getDimension(R.dimen.avatar_size_very_big);

        layoutParams.width = dimen;
        layoutParams.height = dimen;
        avatarImageView.setLayoutParams(layoutParams);
    }

    @Override
    protected void onDetach(@NonNull View view) {
        super.onDetach(view);
        eventBus.unregister(this);
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);
        eventBus.register(this);
    }

    private void loadAvatar() {
        switch (currentConversation.getType()) {
            case ROOM_TYPE_ONE_TO_ONE_CALL:
                avatarImageView.setVisibility(View.VISIBLE);

                ImageRequest imageRequest =
                        DisplayUtils.getImageRequestForUrl(
                                ApiUtils.getUrlForAvatarWithName(userBeingCalled.getBaseUrl(),
                                                                 currentConversation.getName(),
                                                                 R.dimen.avatar_size_very_big),
                                null);

                ImagePipeline imagePipeline = Fresco.getImagePipeline();
                DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);

                dataSource.subscribe(new BaseBitmapDataSubscriber() {
                    @Override
                    protected void onNewResultImpl(@Nullable Bitmap bitmap) {
                        if (avatarImageView != null) {
                            avatarImageView.getHierarchy().setImage(new BitmapDrawable(bitmap), 100,
                                                                    true);

                            if (getResources() != null) {
                                incomingTextRelativeLayout.setBackground(
                                        getResources().getDrawable(R.drawable.incoming_gradient));
                            }

                            if (AvatarStatusCodeHolder.getInstance().getStatusCode() == 200 ||
                                    AvatarStatusCodeHolder.getInstance().getStatusCode() == 0) {
                                if (getActivity() != null) {
                                    Bitmap backgroundBitmap = bitmap.copy(bitmap.getConfig(), true);
                                    new BlurPostProcessor(5, getActivity()).process(backgroundBitmap);
                                    backgroundImageView.setImageDrawable(new BitmapDrawable(backgroundBitmap));
                                }
                            } else if (AvatarStatusCodeHolder.getInstance().getStatusCode() == 201) {
                                ColorArt colorArt = new ColorArt(bitmap);
                                int color = colorArt.getBackgroundColor();

                                float[] hsv = new float[3];
                                Color.colorToHSV(color, hsv);
                                hsv[2] *= 0.75f;
                                color = Color.HSVToColor(hsv);

                                backgroundImageView.setImageDrawable(new ColorDrawable(color));
                            }
                        }
                    }

                    @Override
                    protected void onFailureImpl(DataSource<CloseableReference<CloseableImage>> dataSource) {
                        // unused atm
                    }
                }, UiThreadImmediateExecutorService.getInstance());

                break;
            case ROOM_GROUP_CALL:
                avatarImageView.setImageResource(R.drawable.ic_circular_group);
            case ROOM_PUBLIC_CALL:
                avatarImageView.setImageResource(R.drawable.ic_circular_group);
                break;
            default:
        }
    }

    private void endMediaNotifications() {
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
        AvatarStatusCodeHolder.getInstance().setStatusCode(0);
        leavingScreen = true;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
            handler = null;
        }
        dispose();
        endMediaNotifications();
        super.onDestroy();
    }

    private void dispose() {
        if (disposablesList != null) {
            for (Disposable disposable : disposablesList) {
                if (!disposable.isDisposed()) {
                    disposable.dispose();
                }
            }
        }
    }

    @SuppressLint("LongLogTag")
    private void playRingtoneSound() {
        String callRingtonePreferenceString = appPreferences.getCallRingtoneUri();
        Uri ringtoneUri;

        if (TextUtils.isEmpty(callRingtonePreferenceString)) {
            // play default sound
            ringtoneUri = Uri.parse("android.resource://" + getApplicationContext().getPackageName() +
                                            "/raw/librem_by_feandesign_call");
        } else {
            try {
                RingtoneSettings ringtoneSettings = LoganSquare.parse(
                        callRingtonePreferenceString, RingtoneSettings.class);
                ringtoneUri = ringtoneSettings.getRingtoneUri();
            } catch (IOException e) {
                Log.e(TAG, "Failed to parse ringtone settings");
                ringtoneUri = Uri.parse("android.resource://" + getApplicationContext().getPackageName() +
                                                "/raw/librem_by_feandesign_call");
            }
        }

        if (ringtoneUri != null && getActivity() != null) {
            mediaPlayer = new MediaPlayer();
            try {
                mediaPlayer.setDataSource(getActivity(), ringtoneUri);

                mediaPlayer.setLooping(true);
                AudioAttributes audioAttributes = new AudioAttributes
                        .Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .build();
                mediaPlayer.setAudioAttributes(audioAttributes);

                mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());

                mediaPlayer.prepareAsync();
            } catch (IOException e) {
                Log.e(TAG, "Failed to set data source");
            }
        }
    }
}