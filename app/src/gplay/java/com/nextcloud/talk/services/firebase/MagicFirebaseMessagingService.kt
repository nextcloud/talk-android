/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
package com.nextcloud.talk.services.firebase

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.emoji.text.EmojiCompat
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.bluelinelabs.logansquare.LoganSquare
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.nextcloud.talk.R
import com.nextcloud.talk.activities.MagicCallActivity
import com.nextcloud.talk.jobs.MessageNotificationWorker
import com.nextcloud.talk.models.SignatureVerification
import com.nextcloud.talk.models.json.push.DecryptedPushMessage
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.services.CallService
import com.nextcloud.talk.newarch.utils.MagicJson
import com.nextcloud.talk.utils.NotificationUtils
import com.nextcloud.talk.utils.NotificationUtils.cancelAllNotificationsForAccount
import com.nextcloud.talk.utils.NotificationUtils.cancelExistingNotificationWithId
import com.nextcloud.talk.utils.PushUtils
import com.nextcloud.talk.utils.bundle.BundleKeys
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_INCOMING_PUSH_MESSSAGE
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_OPEN_INCOMING_CALL
import com.nextcloud.talk.utils.bundle.BundleKeys.KEY_USER_ENTITY
import com.nextcloud.talk.utils.preferences.AppPreferences
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.greenrobot.eventbus.EventBus
import org.koin.core.KoinComponent
import org.koin.core.inject
import retrofit2.Retrofit
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException
import java.security.PrivateKey
import javax.crypto.Cipher
import javax.crypto.NoSuchPaddingException

class MagicFirebaseMessagingService : FirebaseMessagingService(), KoinComponent {
    val tag: String = "MagicFirebaseMessagingService"
    val appPreferences: AppPreferences by inject()

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        appPreferences.pushToken = token
    }

    @SuppressLint("LongLogTag")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val incomingCallIntent = Intent(applicationContext, CallService::class.java)
        incomingCallIntent.action = KEY_INCOMING_PUSH_MESSSAGE
        incomingCallIntent.putExtra(BundleKeys.KEY_ENCRYPTED_SUBJECT, remoteMessage.data["subject"])
        incomingCallIntent.putExtra(BundleKeys.KEY_ENCRYPTED_SIGNATURE, remoteMessage.data["signature"])
        applicationContext.startService(incomingCallIntent)
    }

    @SuppressLint("LongLogTag")

    private fun checkIfCallIsActive(signatureVerification: SignatureVerification, decryptedPushMessage: DecryptedPushMessage) {
        /*val ncApi = retrofit.newBuilder().client(okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(CookieManager())).build()).build().create(NcApi::class.java)
        var hasParticipantsInCall = false
        var inCallOnDifferentDevice = false

        ncApi.getPeersForCall(ApiUtils.getCredentials(signatureVerification.userEntity.username, signatureVerification.userEntity.token),
                ApiUtils.getUrlForParticipants(signatureVerification.userEntity.baseUrl,
                        decryptedPushMessage.id))
                .subscribeOn(Schedulers.io())
                .subscribe(object : Observer<ParticipantsOverall> {
                    override fun onSubscribe(d: Disposable) {
                    }

                    override fun onNext(participantsOverall: ParticipantsOverall) {
                        val participantList: List<Participant> = participantsOverall.ocs.data
                        for (participant in participantList) {
                            if (participant.participantFlags != Participant.ParticipantFlags.NOT_IN_CALL) {
                                hasParticipantsInCall = true
                                if (participant.userId == signatureVerification.userEntity.userId) {
                                    inCallOnDifferentDevice = true
                                    break
                                }
                            }
                        }

                        if (!hasParticipantsInCall || inCallOnDifferentDevice) {
                            stopForeground(true)
                        } else if (isServiceInForeground) {
                            checkIfCallIsActive(signatureVerification, decryptedPushMessage)
                        }
                    }

                    override fun onError(e: Throwable) {}
                    override fun onComplete() {
                    }
                })*/

    }
}