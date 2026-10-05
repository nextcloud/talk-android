/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.call

import android.content.Context
import android.net.Uri
import android.telecom.DisconnectCause
import android.util.Log
import androidx.core.telecom.CallAttributesCompat
import androidx.core.telecom.CallControlResult
import androidx.core.telecom.CallControlScope
import androidx.core.telecom.CallsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelecomManager @Inject constructor(private val context: Context) {
    private val callsManager = CallsManager(context)
    private val telecomScope = CoroutineScope(Dispatchers.Main)

    private var currentCallControlScope: CallControlScope? = null

    fun registerAppWithTelecom() {
        try {
            val capabilities: @CallsManager.Companion.Capability Int = CallsManager.CAPABILITY_BASELINE or CallsManager
                .CAPABILITY_SUPPORTS_VIDEO_CALLING
            callsManager.registerAppWithTelecom(capabilities)
            Log.d(TAG, "Registered app with Core-Telecom successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register app with Core-Telecom", e)
        }
    }

    fun hasActiveCall(): Boolean = currentCallControlScope != null

    fun addIncomingCall(
        displayName: String,
        roomToken: String,
        isVideo: Boolean,
        onAnswerCall: () -> Unit,
        onRejectCall: () -> Unit
    ) {
        val callAttributes = CallAttributesCompat(
            displayName = displayName,
            address = Uri.parse("talk:$roomToken"),
            direction = CallAttributesCompat.DIRECTION_INCOMING,
            callType = getCallType(isVideo),
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE
        )

        telecomScope.launch {
            try {
                callsManager.addCall(
                    callAttributes = callAttributes,
                    onAnswer = { _ ->
                        onAnswerCall()
                        CallControlResult.Success()
                    },
                    onDisconnect = { _ ->
                        onRejectCall()
                        CallControlResult.Success()
                    },
                    onSetActive = { CallControlResult.Success() },
                    onSetInactive = { CallControlResult.Success() }
                ) {
                    currentCallControlScope = this
                    Log.d(TAG, "Incoming call registered in CallControlScope")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding incoming call to Telecom", e)
            }
        }
    }

    fun addOutgoingCall(
        displayName: String,
        roomToken: String,
        isVideo: Boolean,
        onDisconnectCall: (() -> Unit)? = null
    ) {
        if (currentCallControlScope != null) {
            Log.d(TAG, "Call already registered in Telecom")
            return
        }

        val callAttributes = CallAttributesCompat(
            displayName = displayName,
            address = Uri.parse("talk:$roomToken"),
            direction = CallAttributesCompat.DIRECTION_OUTGOING,
            callType = getCallType(isVideo),
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE
        )

        telecomScope.launch {
            try {
                callsManager.addCall(
                    callAttributes = callAttributes,
                    onAnswer = { CallControlResult.Success() },
                    onDisconnect = { _ ->
                        onDisconnectCall?.invoke()
                        CallControlResult.Success()
                    },
                    onSetActive = { CallControlResult.Success() },
                    onSetInactive = { CallControlResult.Success() }
                ) {
                    currentCallControlScope = this
                    launch { setActive() }
                    Log.d(TAG, "Outgoing call registered and active in CallControlScope")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding outgoing call to Telecom", e)
            }
        }
    }


    fun endCurrentCall() {
        telecomScope.launch {
            currentCallControlScope?.disconnect(DisconnectCause(DisconnectCause.LOCAL))
            currentCallControlScope = null
        }
    }

    fun setCallActive() {
        telecomScope.launch {
            currentCallControlScope?.setActive()
        }
    }

    private fun getCallType(isVideo: Boolean): Int =
        if (isVideo) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL

    companion object {
        private val TAG = TelecomManager::class.java.simpleName
    }
}
