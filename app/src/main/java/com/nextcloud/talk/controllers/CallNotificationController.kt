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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Vibrator
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
import com.nextcloud.talk.R
import com.nextcloud.talk.api.NcApi
import com.nextcloud.talk.controllers.base.BaseController
import com.nextcloud.talk.events.CallEvent
import com.nextcloud.talk.events.ConfigurationChangeEvent
import com.nextcloud.talk.models.json.conversations.Conversation
import com.nextcloud.talk.models.json.participants.ParticipantsOverall
import com.nextcloud.talk.newarch.data.model.ErrorModel
import com.nextcloud.talk.newarch.data.source.remote.ApiErrorHandler
import com.nextcloud.talk.newarch.domain.usecases.GetParticipantsForCallUseCase
import com.nextcloud.talk.newarch.domain.usecases.base.UseCaseResponse
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.newarch.local.models.getCredentials
import com.nextcloud.talk.newarch.local.models.toUser
import com.nextcloud.talk.newarch.services.CallService
import com.nextcloud.talk.newarch.utils.NetworkComponents
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.singletons.AvatarStatusCodeHolder
import kotlinx.coroutines.*
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.koin.android.ext.android.inject
import org.koin.core.parameter.parametersOf
import org.michaelevans.colorart.library.ColorArt
import org.parceler.Parcels

class CallNotificationController(private val originalBundle: Bundle) : BaseController() {

    val scope = CoroutineScope(Job() + Dispatchers.IO)
    val ncApi: NcApi by inject()
    val networkComponents: NetworkComponents by inject()
    val apiErrorHandler: ApiErrorHandler by inject()

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
    private val conversation: Conversation = Parcels.unwrap(originalBundle.getParcelable(BundleKeys.KEY_CONVERSATION))
    private val userBeingCalled: UserNgEntity = originalBundle.getParcelable(BundleKeys.KEY_USER_ENTITY)!!
    private val activeNotification: String? = originalBundle.getString(BundleKeys.KEY_ACTIVE_NOTIFICATION)

    private val mediaPlayer: MediaPlayer = MediaPlayer()
    private var vibrator: Vibrator? = null

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

    private fun dismissIncomingCallNotification() {
        if (activeNotification != null) {
            val hideIncomingCallNotificationIntent = Intent(applicationContext, CallService::class.java)
            hideIncomingCallNotificationIntent.action = BundleKeys.DISMISS_CALL_NOTIFICATION
            hideIncomingCallNotificationIntent.putExtra(BundleKeys.KEY_ACTIVE_NOTIFICATION, activeNotification)
            applicationContext?.startService(hideIncomingCallNotificationIntent)
        }
    }

    @OnClick(R.id.callControlHangupView)
    internal fun hangup() {
        dismissIncomingCallNotification()
        activity?.finish()
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
        dismissIncomingCallNotification()
        originalBundle.putString(BundleKeys.KEY_CONVERSATION_TOKEN, conversation.token)
        router.replaceTopController(
                RouterTransaction.with(CallController(originalBundle))
                        .popChangeHandler(HorizontalChangeHandler())
                        .pushChangeHandler(HorizontalChangeHandler())
        )
    }

    @SuppressLint("LongLogTag")
    override fun onViewBound(view: View) {
        super.onViewBound(view)
        conversationNameTextView?.text = conversation.displayName
        handleIncomingConversation()
        loadAvatar()
        callAnswerCameraView?.visibility = View.VISIBLE
        callAnswerVoiceOnlyView?.visibility = View.VISIBLE
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onMessageEvent(callEvent: CallEvent) {
        activity?.finish()
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
        when (conversation.type) {
            Conversation.ConversationType.ONE_TO_ONE_CONVERSATION -> {
                avatarImageView!!.visibility = View.VISIBLE

                incomingTextRelativeLayout?.background =
                        resources?.getDrawable(R.drawable.incoming_gradient)
                avatarImageView?.load(
                        ApiUtils.getUrlForAvatarWithName(
                                userBeingCalled.baseUrl,
                                conversation.name, R.dimen.avatar_size_very_big
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
                                    val newBitmap = BlurTransformation(activity!!, 8f).transform(
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

    private fun handleIncomingConversation() {
        if (activeNotification == null) {
            playSoundAndVibrate()
            checkIsConversationActive()
        }
    }

    private fun playSoundAndVibrate() {
        activity?.let {
            mediaPlayer.setDataSource(it, NotificationUtils.getCallSoundUri(it, appPreferences)!!)
            mediaPlayer.isLooping = true
            val audioAttributes: AudioAttributes = AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST).build()
            mediaPlayer.setAudioAttributes(audioAttributes)
            mediaPlayer.setOnPreparedListener { mediaPlayer.start() }
            mediaPlayer.prepareAsync()
            vibrator = applicationContext?.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (vibrator?.hasVibrator() == true) {
                vibrator?.vibrate(5000)
            }
        }
    }

    private fun checkIsConversationActive() {
        run {
            val getParticipantsForCallUseCase = GetParticipantsForCallUseCase(networkComponents.getRepository(false, userBeingCalled.toUser()), apiErrorHandler)
            getParticipantsForCallUseCase.invoke(scope, parametersOf(userBeingCalled, conversation.token), object : UseCaseResponse<ParticipantsOverall> {
                override suspend fun onSuccess(result: ParticipantsOverall) {
                    val participants = result.ocs.data
                    if (participants.size > 0) {
                        val activeParticipants = participants.filter { it.sessionId != null && !it.sessionId.equals("0") }
                        val activeOnAnotherDevice = activeParticipants.filter { it.userId == userBeingCalled.userId }
                        if (activeParticipants.isNotEmpty() && activeOnAnotherDevice.isEmpty()) {
                            delay(5000)
                            checkIsConversationActive()
                        } else {
                            withContext(Dispatchers.Main) {
                                activity?.finish()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            activity?.finish()
                        }
                    }
                }

                override suspend fun onError(errorModel: ErrorModel?) {
                    withContext(Dispatchers.Main) {
                        activity?.finish()
                    }
                }
            })
        }
    }

    public override fun onDestroy() {
        AvatarStatusCodeHolder.getInstance().statusCode = 0
        scope.cancel()
        mediaPlayer.stop()
        mediaPlayer.release()
        vibrator?.cancel()
        super.onDestroy()
    }

    companion object {

        private val TAG = "CallNotificationController"
    }
}