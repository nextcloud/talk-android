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
import android.graphics.drawable.Drawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.*;
import android.renderscript.RenderScript;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import autodagger.AutoInjector;
import butterknife.BindView;
import butterknife.OnClick;
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
import com.facebook.imagepipeline.postprocessors.RoundAsCirclePostprocessor;
import com.facebook.imagepipeline.request.ImageRequest;
import com.nextcloud.talk.R;
import com.nextcloud.talk.api.NcApi;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.events.ConfigurationChangeEvent;
import com.nextcloud.talk.models.RingtoneSettings;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.models.json.rooms.Conversation;
import com.nextcloud.talk.models.json.rooms.RoomsOverall;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.DoNotDisturbUtils;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.preferences.AppPreferences;
import com.nextcloud.talk.utils.singletons.AvatarStatusCodeHolder;
import io.reactivex.Observer;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import okhttp3.Cache;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.michaelevans.colorart.library.ColorArt;
import org.parceler.Parcels;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
    private Vibrator vibrator;
    private Handler handler;

    public CallNotificationController(Bundle args) {
        super(args);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        this.roomId = args.getString(BundleKeys.KEY_ROOM_ID, "");
        this.currentConversation = Parcels.unwrap(args.getParcelable(BundleKeys.KEY_ROOM));
        this.userBeingCalled = args.getParcelable(BundleKeys.KEY_USER_ENTITY);

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
        originalBundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, false);
        proceedToCall();
    }

    @OnClick(R.id.callAnswerVoiceOnlyView)
    void answerVoiceOnly() {
        originalBundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, true);
        proceedToCall();
    }

    private void proceedToCall() {
        originalBundle.putString(BundleKeys.KEY_ROOM_TOKEN, currentConversation.getToken());

        getRouter().replaceTopController(RouterTransaction.with(new CallController(originalBundle))
                .popChangeHandler(new HorizontalChangeHandler())
                .pushChangeHandler(new HorizontalChangeHandler()));
    }

    private void checkIfAnyParticipantsRemainInRoom() {
        ncApi.getPeersForCall(credentials, ApiUtils.getUrlForParticipants(userBeingCalled.getBaseUrl(),
                currentConversation.getToken()))
                .subscribeOn(Schedulers.io())
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
                            if (participant.getParticipantFlags() != Participant.ParticipantFlags.NOT_IN_CALL) {
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
                .subscribeOn(Schedulers.io())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<RoomsOverall>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        disposablesList.add(d);
                    }

                    @Override
                    public void onNext(RoomsOverall roomsOverall) {
                        for (Conversation conversation : roomsOverall.getOcs().getData()) {
                            if (roomId.equals(conversation.getRoomId())) {
                                currentConversation = conversation;
                                runAllThings();
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

    private void runAllThings() {
        if (conversationNameTextView != null) {
            conversationNameTextView.setText(currentConversation.getDisplayName());
        }

        loadAvatar();
        checkIfAnyParticipantsRemainInRoom();
        showAnswerControls();
    }

    @SuppressLint("LongLogTag")
    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);

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

        if (DoNotDisturbUtils.shouldPlaySound()) {
            String callRingtonePreferenceString = appPreferences.getCallRingtoneUri();
            Uri ringtoneUri;

            if (TextUtils.isEmpty(callRingtonePreferenceString)) {
                // play default sound
                ringtoneUri = Uri.parse("android.resource://" + getApplicationContext().getPackageName() +
                        "/raw/librem_by_feandesign_call");
            } else {
                try {
                    RingtoneSettings ringtoneSettings = LoganSquare.parse(callRingtonePreferenceString, RingtoneSettings.class);
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
                    AudioAttributes audioAttributes = new AudioAttributes.Builder().setContentType(AudioAttributes
                            .CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build();
                    mediaPlayer.setAudioAttributes(audioAttributes);

                    mediaPlayer.setOnPreparedListener(mp -> mediaPlayer.start());

                    mediaPlayer.prepareAsync();
                } catch (IOException e) {
                    Log.e(TAG, "Failed to set data source");
                }
            }
        }

        if (DoNotDisturbUtils.shouldVibrate(appPreferences.getShouldVibrateSetting())) {
            vibrator = (Vibrator) getApplicationContext().getSystemService(Context.VIBRATOR_SERVICE);

            if (vibrator != null) {
                long[] vibratePattern = new long[]{0, 400, 800, 600, 800, 800, 800, 1000};
                int[] amplitudes = new int[]{0, 255, 0, 255, 0, 255, 0, 255};

                VibrationEffect vibrationEffect;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (vibrator.hasAmplitudeControl()) {
                        vibrationEffect = VibrationEffect.createWaveform(vibratePattern, amplitudes, -1);
                        vibrator.vibrate(vibrationEffect);
                    } else {
                        vibrationEffect = VibrationEffect.createWaveform(vibratePattern, -1);
                        vibrator.vibrate(vibrationEffect);
                    }
                } else {
                    vibrator.vibrate(vibratePattern, -1);
                }
            }

            handler.postDelayed(() -> {
                if (vibrator != null) {
                    vibrator.cancel();
                }
            }, 10000);
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
                        DisplayUtils.getImageRequestForUrl(ApiUtils.getUrlForAvatarWithName(userBeingCalled.getBaseUrl(),
                                currentConversation.getName(), R.dimen.avatar_size_very_big), null);

                ImagePipeline imagePipeline = Fresco.getImagePipeline();
                DataSource<CloseableReference<CloseableImage>> dataSource = imagePipeline.fetchDecodedImage(imageRequest, null);

                dataSource.subscribe(new BaseBitmapDataSubscriber() {
                    @Override
                    protected void onNewResultImpl(@Nullable Bitmap bitmap) {
                        if (avatarImageView != null) {
                            avatarImageView.getHierarchy().setImage(new BitmapDrawable(bitmap), 100,
                                    true);

                            if (getResources() != null) {
                                incomingTextRelativeLayout.setBackground(getResources().getDrawable(R.drawable
                                        .incoming_gradient));
                            }

                            if ((AvatarStatusCodeHolder.getInstance().getStatusCode() == 200 || AvatarStatusCodeHolder.getInstance().getStatusCode() == 0) &&
                                    userBeingCalled.hasSpreedCapabilityWithName("no-ping")) {
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
                    }
                }, UiThreadImmediateExecutorService.getInstance());

                break;
            case ROOM_GROUP_CALL:
                avatarImageView.getHierarchy().setImage(DisplayUtils.getRoundedDrawable(context.getDrawable(R.drawable.ic_people_group_white_24px))
                        , 100, true);
            case ROOM_PUBLIC_CALL:
                avatarImageView.getHierarchy().setImage(DisplayUtils.getRoundedDrawable(context.getDrawable(R.drawable.ic_people_group_white_24px))
                        , 100, true);
                break;
            default:
        }
    }

    private void endMediaAndVibratorNotifications() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (vibrator != null) {
            vibrator.cancel();
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
        endMediaAndVibratorNotifications();
        super.onDestroy();
    }

    private void dispose() {
        Disposable disposable;
        for (int i = 0; i < disposablesList.size(); i++) {
            if (!(disposable = disposablesList.get(i)).isDisposed()) {
                disposable.dispose();
            }
        }
    }
}