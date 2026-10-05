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
import androidx.annotation.VisibleForTesting
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
    private var currentRoomToken: String? = null
    private var isPendingSetActive: Boolean = false
    private var isLocallyDisconnecting: Boolean = false

    fun registerAppWithTelecom() {
        telecomScope.launch(Dispatchers.IO) {
            try {
                val capabilities: @CallsManager.Companion.Capability Int =
                    CallsManager.CAPABILITY_BASELINE or CallsManager.CAPABILITY_SUPPORTS_VIDEO_CALLING
                callsManager.registerAppWithTelecom(capabilities)
                Log.d(TAG, "Registered app with Core-Telecom successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register app with Core-Telecom", e)
            }
        }
    }

    fun hasActiveCall(roomToken: String? = null): Boolean {
        if (roomToken.isNullOrBlank()) {
            return currentRoomToken != null || currentCallControlScope != null
        }
        return currentRoomToken == roomToken
    }

    fun addIncomingCall(
        displayName: String,
        roomToken: String,
        isVideo: Boolean,
        onAnswerCall: () -> Unit,
        onRejectCall: () -> Unit
    ) {
        if (!prepareCallRegistration(roomToken, "Incoming")) return

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
                        val wasLocal = isLocallyDisconnecting
                        handleCallDisconnected(roomToken)
                        if (!wasLocal) {
                            onRejectCall()
                        }
                        CallControlResult.Success()
                    },
                    onSetActive = { CallControlResult.Success() },
                    onSetInactive = { CallControlResult.Success() }
                ) {
                    onScopeAssigned(roomToken)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding incoming call to Telecom for room $roomToken", e)
            } finally {
                handleCallDisconnected(roomToken)
            }
        }
    }

    fun addOutgoingCall(
        displayName: String,
        roomToken: String,
        isVideo: Boolean,
        onDisconnectCall: (() -> Unit)? = null
    ) {
        if (!prepareCallRegistration(roomToken, "Outgoing")) return

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
                        val wasLocal = isLocallyDisconnecting
                        handleCallDisconnected(roomToken)
                        if (!wasLocal) {
                            onDisconnectCall?.invoke()
                        }
                        CallControlResult.Success()
                    },
                    onSetActive = { CallControlResult.Success() },
                    onSetInactive = { CallControlResult.Success() }
                ) {
                    onScopeAssigned(roomToken, autoActivate = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding outgoing call to Telecom for room $roomToken", e)
            } finally {
                handleCallDisconnected(roomToken)
            }
        }
    }

    fun endCurrentCall(roomToken: String? = null) {
        if (roomToken != null && currentRoomToken != null && currentRoomToken != roomToken) {
            Log.d(TAG, "endCurrentCall ignored: target room $roomToken does not match active room $currentRoomToken")
            return
        }
        val scopeToDisconnect = currentCallControlScope
        isLocallyDisconnecting = true
        handleCallDisconnected(currentRoomToken ?: roomToken ?: "")

        telecomScope.launch {
            try {
                scopeToDisconnect?.disconnect(DisconnectCause(DisconnectCause.LOCAL))
            } finally {
                isLocallyDisconnecting = false
            }
        }
    }

    fun setCallActive(roomToken: String? = null) {
        if (roomToken != null && currentRoomToken != null && currentRoomToken != roomToken) {
            Log.d(TAG, "setCallActive ignored: target room $roomToken does not match active room $currentRoomToken")
            return
        }
        val scope = currentCallControlScope
        if (scope != null) {
            telecomScope.launch {
                scope.setActive()
            }
        } else if (currentRoomToken != null) {
            isPendingSetActive = true
            Log.d(TAG, "setCallActive queued: registration pending for room $currentRoomToken")
        }
    }

    private fun prepareCallRegistration(roomToken: String, callTypeLabel: String): Boolean {
        if (roomToken.isBlank()) {
            Log.e(TAG, "Cannot add $callTypeLabel call: roomToken is blank")
            return false
        }
        if (currentRoomToken == roomToken || currentCallControlScope != null) {
            Log.d(TAG, "$callTypeLabel call ignored: registration or scope already active for room $currentRoomToken")
            return false
        }
        if (currentRoomToken != null) {
            Log.d(TAG, "$callTypeLabel call ignored: active call already exists for room $currentRoomToken")
            return false
        }
        currentRoomToken = roomToken
        isPendingSetActive = false
        return true
    }

    private fun handleCallDisconnected(roomToken: String) {
        if (roomToken.isNotEmpty() && (currentRoomToken == roomToken || currentRoomToken == null)) {
            currentCallControlScope = null
            currentRoomToken = null
            isPendingSetActive = false
        }
    }

    private fun CallControlScope.onScopeAssigned(roomToken: String, autoActivate: Boolean = false) {
        if (currentRoomToken == roomToken) {
            currentCallControlScope = this
            Log.d(TAG, "Call registered in CallControlScope for room $roomToken")
            if (autoActivate || isPendingSetActive) {
                launch { setActive() }
                isPendingSetActive = false
                Log.d(TAG, "Call set active for room $roomToken")
            }
        } else {
            Log.w(TAG, "CallControlScope obtained for stale room $roomToken, disconnecting")
            launch { disconnect(DisconnectCause(DisconnectCause.CANCELED)) }
        }
    }

    @VisibleForTesting
    internal fun getCallType(isVideo: Boolean): Int =
        if (isVideo) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL

    companion object {
        private val TAG = TelecomManager::class.java.simpleName
    }
}
