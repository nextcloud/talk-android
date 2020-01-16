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

package com.nextcloud.talk.controllers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import butterknife.BindView
import butterknife.OnClick
import coil.api.load
import coil.bitmappool.BitmapPool
import coil.drawable.CrossfadeDrawable
import coil.size.OriginalSize
import coil.transform.BlurTransformation
import coil.transform.CircleCropTransformation
import com.bluelinelabs.conductor.RouterTransaction
import com.bluelinelabs.conductor.changehandler.HorizontalChangeHandler
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.events.ConfigurationChangeEvent
import com.nextcloud.talk.models.RingtoneSettings
import com.nextcloud.talk.models.database.ArbitraryStorageEntity
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.participants.Participant
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.DoNotDisturbUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.database.arbitrarystorage.ArbitraryStorageUtils
import com.nextcloud.talk.utils.singletons.AvatarStatusCodeHolder
import com.uber.autodispose.AutoDispose
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.koin.android.ext.android.inject
import org.michaelevans.colorart.library.ColorArt
import org.parceler.Parcels
import java.io.IOException

class CallNotificationController(private val originalBundle: Bundle) : BaseController() {

    val ncApi: NcApi by inject()
    val arbitraryStorageUtils: ArbitraryStorageUtils by inject()

    @JvmField
    @BindView(R.id.conversationNameTextView)
    var conversationNameTextView: TextView? = null

    @JvmField
    @BindView(R.id.avatarImageView)
    var avatarImageView: ImageView? = null

    @JvmField
    @BindView(R.id.callAnswerVoiceOnlyView)
    var callAnswerVoiceOnlyView: ImageView? = null

    @JvmField
    @BindView(R.id.callAnswerCameraView)
    var callAnswerCameraView: ImageView? = null

    @JvmField
    @BindView(R.id.backgroundImageView)
    var backgroundImageView: ImageView? = null

    @JvmField
    @BindView(R.id.incomingTextRelativeLayout)
    var incomingTextRelativeLayout: RelativeLayout? = null
    private val roomId: String
    private val userBeingCalled: UserNgEntity?
    private val credentials: String?
    private var currentConversation: Conversation? = null
    private var mediaPlayer: MediaPlayer? = null
    private var leavingScreen = false
    private var vibrator: Vibrator? = null
    private var handler: Handler? = null

    init {
        this.roomId = originalBundle.getString(BundleKeys.KEY_ROOM_ID, "")
        this.currentConversation = Parcels.unwrap(originalBundle.getParcelable(BundleKeys.KEY_ROOM))
        this.userBeingCalled = originalBundle.getParcelable(BundleKeys.KEY_USER_ENTITY)
        credentials = ApiUtils.getCredentials(userBeingCalled!!.username, userBeingCalled.token)
    }

    override fun inflateView(
            inflater: LayoutInflater,
            container: ViewGroup
    ): View {
        return inflater.inflate(R.layout.controller_call_notification, container, false)
    }

    override fun onDetach(view: View) {
        eventBus.unregister(this)
        super.onDetach(view)
    }

    override fun onAttach(view: View) {
        super.onAttach(view)
        eventBus.register(this)
    }

    private fun showAnswerControls() {
        callAnswerCameraView!!.visibility = View.VISIBLE
        callAnswerVoiceOnlyView!!.visibility = View.VISIBLE
    }

    @OnClick(R.id.callControlHangupView)
    internal fun hangup() {
        leavingScreen = true

        if (activity != null) {
            activity!!.finish()
        }
    }

    @OnClick(R.id.callAnswerCameraView)
    internal fun answerWithCamera() {
        originalBundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, false)
        proceedToCall()
    }

    @OnClick(R.id.callAnswerVoiceOnlyView)
    internal fun answerVoiceOnly() {
        originalBundle.putBoolean(BundleKeys.KEY_CALL_VOICE_ONLY, true)
        proceedToCall()
    }

    private fun proceedToCall() {
        originalBundle.putString(
                BundleKeys.KEY_ROOM_TOKEN,
                currentConversation!!.token
        )

        router.replaceTopController(
                RouterTransaction.with(CallController(originalBundle))
                        .popChangeHandler(HorizontalChangeHandler())
                        .pushChangeHandler(HorizontalChangeHandler())
        )
    }

    private fun checkIfAnyParticipantsRemainInRoom() {
        ncApi.getPeersForCall(
                credentials, ApiUtils.getUrlForParticipants(
                userBeingCalled!!.baseUrl,
                currentConversation!!.token
        )
        )
                .subscribeOn(Schedulers.io())
                .takeWhile { observable -> !leavingScreen }
                .retry(3)
                .`as`(AutoDispose.autoDisposable(scopeProvider))
                .subscribe(object : Observer<ParticipantsOverall> {
                    override fun onSubscribe(d: Disposable) {}

                    override fun onNext(participantsOverall: ParticipantsOverall) {
                        var hasParticipantsInCall = false
                        var inCallOnDifferentDevice = false
                        val participantList = participantsOverall.ocs.data
                        for (participant in participantList) {
                            if (participant.participantFlags != Participant.ParticipantFlags.NOT_IN_CALL) {
                                hasParticipantsInCall = true

                                if (participant.userId == userBeingCalled.userId) {
                                    inCallOnDifferentDevice = true
                                    break
                                }
                            }
                        }

                        if (!hasParticipantsInCall || inCallOnDifferentDevice) {
                            if (activity != null) {
                                activity!!.runOnUiThread { hangup() }
                            }
                        }
                    }

                    override fun onError(e: Throwable) {

                    }

                    override fun onComplete() {
                        if (!leavingScreen) {
                            checkIfAnyParticipantsRemainInRoom()
                        }
                    }
                })
    }

    private fun handleFromNotification() {
        ncApi.getRooms(credentials, ApiUtils.getUrlForGetRooms(userBeingCalled!!.baseUrl))
                .subscribeOn(Schedulers.io())
                .retry(3)
                .observeOn(AndroidSchedulers.mainThread())
                .`as`(AutoDispose.autoDisposable(scopeProvider))
                .subscribe(object : Observer<RoomsOverall> {
                    override fun onSubscribe(d: Disposable) {}

                    override fun onNext(roomsOverall: RoomsOverall) {
                        for (conversation in roomsOverall.ocs.data) {
                            if (roomId == conversation.conversationId) {
                                currentConversation = conversation
                                runAllThings()
                                break
                            }
                        }
                    }

                    override fun onError(e: Throwable) {

                    }

                    override fun onComplete() {

                    }
                })
    }

    private fun runAllThings() {
        if (conversationNameTextView != null) {
            conversationNameTextView!!.text = currentConversation!!.displayName
        }

        loadAvatar()
        checkIfAnyParticipantsRemainInRoom()
        showAnswerControls()
    }

    @SuppressLint("LongLogTag")
    override fun onViewBound(view: View) {
        super.onViewBound(view)

        if (handler == null) {
            handler = Handler()
        }

        if (currentConversation == null) {
            handleFromNotification()
        } else {
            runAllThings()
        }

        var importantConversation = false
        val arbitraryStorageEntity: ArbitraryStorageEntity? = arbitraryStorageUtils.getStorageSetting(
                userBeingCalled!!.id!!,
                "important_conversation",
                currentConversation!!.token
        )

        if (arbitraryStorageEntity != null) {
            importantConversation = arbitraryStorageEntity.value!!.toBoolean()
        }

        if (DoNotDisturbUtils.shouldPlaySound(importantConversation)) {
            val callRingtonePreferenceString = appPreferences.callRingtoneUri
            var ringtoneUri: Uri?

            if (TextUtils.isEmpty(callRingtonePreferenceString)) {
                // play default sound
                ringtoneUri = Uri.parse(
                        "android.resource://" + applicationContext!!.packageName +
                                "/raw/librem_by_feandesign_call"
                )
            } else {
                ringtoneUri = try {
                    val ringtoneSettings =
                            LoganSquare.parse(callRingtonePreferenceString, RingtoneSettings::class.java)
                    ringtoneSettings.ringtoneUri
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to parse ringtone settings")
                    Uri.parse(
                            "android.resource://" + applicationContext!!.packageName +
                                    "/raw/librem_by_feandesign_call"
                    )
                }

            }

            if (ringtoneUri != null && activity != null) {
                mediaPlayer = MediaPlayer()
                try {
                    mediaPlayer!!.setDataSource(activity!!, ringtoneUri)

                    mediaPlayer!!.isLooping = true
                    val audioAttributes = AudioAttributes.Builder()
                            .setContentType(
                                    AudioAttributes
                                            .CONTENT_TYPE_SONIFICATION
                            )
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .build()
                    mediaPlayer!!.setAudioAttributes(audioAttributes)

                    mediaPlayer!!.setOnPreparedListener { mp -> mediaPlayer!!.start() }

                    mediaPlayer!!.prepareAsync()
                } catch (e: IOException) {
                    Log.e(TAG, "Failed to set data source")
                }

            }
        }

        if (DoNotDisturbUtils.shouldVibrate(appPreferences.shouldVibrateSetting)) {
            vibrator = applicationContext!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            if (vibrator != null) {
                val vibratePattern = longArrayOf(0, 400, 800, 600, 800, 800, 800, 1000)
                val amplitudes = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)

                val vibrationEffect: VibrationEffect
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    if (vibrator!!.hasAmplitudeControl()) {
                        vibrationEffect = VibrationEffect.createWaveform(vibratePattern, amplitudes, -1)
                        vibrator!!.vibrate(vibrationEffect)
                    } else {
                        vibrationEffect = VibrationEffect.createWaveform(vibratePattern, -1)
                        vibrator!!.vibrate(vibrationEffect)
                    }
                } else {
                    vibrator!!.vibrate(vibratePattern, -1)
                }
            }

            handler!!.postDelayed({
                if (vibrator != null) {
                    vibrator!!.cancel()
                }
            }, 10000)
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(configurationChangeEvent: ConfigurationChangeEvent) {
        val layoutParams = avatarImageView!!.layoutParams as ConstraintLayout.LayoutParams
        val dimen = resources!!.getDimension(R.dimen.avatar_size_very_big)
                .toInt()

        layoutParams.width = dimen
        layoutParams.height = dimen
        avatarImageView!!.layoutParams = layoutParams
    }

    private fun loadAvatar() {
        when (currentConversation!!.type) {
            Conversation.ConversationType.ONE_TO_ONE_CONVERSATION -> {
                avatarImageView!!.visibility = View.VISIBLE

                incomingTextRelativeLayout?.background =
                        resources?.getDrawable(R.drawable.incoming_gradient)
                avatarImageView?.load(
                        ApiUtils.getUrlForAvatarWithName(
                                userBeingCalled!!.baseUrl,
                                currentConversation!!.name, R.dimen.avatar_size_very_big
                        )
                ) {
                    addHeader("Authorization", userBeingCalled.getCredentials())
                    transformations(CircleCropTransformation())
                    listener(onSuccess = { data, dataSource ->
                        GlobalScope.launch {
                            val bitmapFromImageView: Bitmap

                            if (avatarImageView!!.drawable is CrossfadeDrawable) {
                                bitmapFromImageView = ((avatarImageView!!.drawable as CrossfadeDrawable).end as BitmapDrawable).bitmap
                            } else {
                                bitmapFromImageView = ((avatarImageView!!.drawable) as BitmapDrawable).bitmap
                            }

                            if ((AvatarStatusCodeHolder.getInstance().statusCode == 200 || AvatarStatusCodeHolder.getInstance().statusCode == 0)) {

                                if (activity != null) {
                                    val newBitmap = BlurTransformation(activity!!, 5f).transform(
                                            BitmapPool(10000000), bitmapFromImageView, OriginalSize
                                    )
                                    withContext(Dispatchers.Main) {
                                        backgroundImageView!!.setImageBitmap(newBitmap)
                                    }
                                }
                            } else if (AvatarStatusCodeHolder.getInstance().statusCode == 201) {
                                val colorArt = ColorArt(bitmapFromImageView)
                                var color = colorArt.backgroundColor

                                val hsv = FloatArray(3)
                                Color.colorToHSV(color, hsv)
                                hsv[2] *= 0.75f
                                color = Color.HSVToColor(hsv)

                                withContext(Dispatchers.Main) {
                                    backgroundImageView!!.setImageDrawable(ColorDrawable(color))
                                }
                            }
                        }
                    })

                }
            }

            Conversation.ConversationType.GROUP_CONVERSATION -> {
                avatarImageView?.load(R.drawable.ic_people_group_white_24px_with_circle) {
                    transformations(CircleCropTransformation())
                }
            }
            Conversation.ConversationType.PUBLIC_CONVERSATION -> {
                avatarImageView?.load(R.drawable.ic_link_white_24px_with_circle) {
                    transformations(CircleCropTransformation())
                }

            }

            else -> {
                // do nothing
            }
        }
    }

    private fun endMediaAndVibratorNotifications() {
        if (mediaPlayer != null) {
            if (mediaPlayer!!.isPlaying) {
                mediaPlayer!!.stop()
            }

            mediaPlayer!!.release()
            mediaPlayer = null
        }

        if (vibrator != null) {
            vibrator!!.cancel()
        }
    }

    public override fun onDestroy() {
        AvatarStatusCodeHolder.getInstance()
                .statusCode = 0
        leavingScreen = true
        if (handler != null) {
            handler!!.removeCallbacksAndMessages(null)
            handler = null
        }
        endMediaAndVibratorNotifications()
        super.onDestroy()
    }

    companion object {

        private val TAG = "CallNotificationController"
    }
}