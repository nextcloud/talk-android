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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelecomManager @Inject constructor(private val context: Context) {
    private val callsManager = CallsManager(context)

    // SupervisorJob: one failing Telecom operation must not cancel the scope for all later calls.
    // The handler keeps exceptions from a closed CallSession (e.g. remote end racing a local hangup) from
    // crashing the app.
    private val telecomScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Unhandled error in Core-Telecom operation", throwable)
        }
    )

    // addCall must not run before the app is registered, which matters when a call push cold-starts the process
    @Volatile private var registrationJob: Job? = null

    @Volatile private var currentCallControlScope: CallControlScope? = null

    @Volatile private var currentRoomToken: String? = null

    @Volatile private var currentRegistrationId: String? = null

    @Volatile private var isPendingSetActive: Boolean = false

    @Volatile private var currentDisconnectHandler: (() -> Unit)? = null

    @Volatile private var isIncomingCall: Boolean = false

    @Volatile private var isCallAnsweredOrActive: Boolean = false

    @Volatile private var currentCallType: Int = CallAttributesCompat.CALL_TYPE_AUDIO_CALL

    fun registerAppWithTelecom() {
        registrationJob = telecomScope.launch(Dispatchers.IO) {
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
        val registrationId = prepareCallRegistration(roomToken, "Incoming") ?: return
        currentDisconnectHandler = onRejectCall
        isIncomingCall = true
        isCallAnsweredOrActive = false
        currentCallType = getCallType(isVideo)

        val callAttributes = CallAttributesCompat(
            displayName = displayName,
            address = Uri.parse("talk:$roomToken"),
            direction = CallAttributesCompat.DIRECTION_INCOMING,
            callType = getCallType(isVideo),
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE
        )

        telecomScope.launch {
            try {
                registrationJob?.join()
                callsManager.addCall(
                    callAttributes = callAttributes,
                    onAnswer = { _ ->
                        isIncomingCall = false
                        isCallAnsweredOrActive = true
                        onAnswerCall()
                    },
                    onDisconnect = { _ ->
                        val handlerToInvoke = currentDisconnectHandler
                        handleCallDisconnected(registrationId)
                        handlerToInvoke?.invoke()
                    },
                    onSetActive = { },
                    onSetInactive = { }
                ) {
                    onScopeAssigned(registrationId, roomToken)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding incoming call to Telecom for room $roomToken", e)
            } finally {
                handleCallDisconnected(registrationId)
            }
        }
    }

    fun addOutgoingCall(
        displayName: String,
        roomToken: String,
        isVideo: Boolean,
        onDisconnectCall: (() -> Unit)? = null
    ) {
        val registrationId = prepareCallRegistration(roomToken, "Outgoing") ?: return
        currentDisconnectHandler = onDisconnectCall
        isIncomingCall = false
        isCallAnsweredOrActive = true
        currentCallType = getCallType(isVideo)

        val callAttributes = CallAttributesCompat(
            displayName = displayName,
            address = Uri.parse("talk:$roomToken"),
            direction = CallAttributesCompat.DIRECTION_OUTGOING,
            callType = getCallType(isVideo),
            callCapabilities = CallAttributesCompat.SUPPORTS_SET_INACTIVE
        )

        telecomScope.launch {
            try {
                registrationJob?.join()
                callsManager.addCall(
                    callAttributes = callAttributes,
                    onAnswer = { },
                    onDisconnect = { _ ->
                        val handlerToInvoke = currentDisconnectHandler
                        handleCallDisconnected(registrationId)
                        handlerToInvoke?.invoke()
                    },
                    onSetActive = { },
                    onSetInactive = { }
                ) {
                    onScopeAssigned(registrationId, roomToken, autoActivate = true)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error adding outgoing call to Telecom for room $roomToken", e)
            } finally {
                handleCallDisconnected(registrationId)
            }
        }
    }

    fun endCurrentCall(roomToken: String? = null) {
        val activeRoom = currentRoomToken ?: return
        if (!roomToken.isNullOrBlank() && activeRoom != roomToken) {
            Log.d(TAG, "endCurrentCall ignored: target room $roomToken does not match active room $activeRoom")
            return
        }
        if (roomToken.isNullOrBlank() && isCallAnsweredOrActive) {
            Log.d(TAG, "endCurrentCall ignored: blank roomToken provided for active call")
            return
        }

        val scopeToDisconnect = currentCallControlScope
        val registrationIdToClear = currentRegistrationId
        handleCallDisconnected(registrationIdToClear ?: "")

        telecomScope.launch {
            scopeToDisconnect?.disconnect(DisconnectCause(DisconnectCause.LOCAL))
        }
    }

    /**
     * Ends the call for [roomToken] only while it is still ringing (incoming, not yet answered or activated).
     * Safe to call from places that only know the ring has stopped (caller hung up, answered on another device,
     * ring timeout, notification gone): it never tears down a call the user has already picked up.
     */
    fun endRingingCall(roomToken: String?, disconnectCause: Int = DisconnectCause.MISSED) {
        if (roomToken.isNullOrBlank() || currentRoomToken != roomToken) {
            return
        }
        if (!isIncomingCall || isCallAnsweredOrActive) {
            Log.d(TAG, "endRingingCall ignored: call for room $roomToken is not ringing anymore")
            return
        }

        val scopeToDisconnect = currentCallControlScope
        handleCallDisconnected(currentRegistrationId ?: "")

        telecomScope.launch {
            scopeToDisconnect?.disconnect(DisconnectCause(disconnectCause))
        }
    }

    /**
     * @param isVideo the call type the user actually joined with (e.g. "answer voice only" on a video call);
     * null keeps the type the call was registered with.
     */
    fun setCallActive(roomToken: String? = null, isVideo: Boolean? = null, onDisconnectCall: (() -> Unit)? = null) {
        if (roomToken != null && currentRoomToken != null && currentRoomToken != roomToken) {
            Log.d(TAG, "setCallActive ignored: target room $roomToken does not match active room $currentRoomToken")
            return
        }

        if (isVideo != null) {
            currentCallType = getCallType(isVideo)
        }

        if (onDisconnectCall != null) {
            currentDisconnectHandler = onDisconnectCall
        }

        isCallAnsweredOrActive = true

        val scope = currentCallControlScope
        if (scope != null) {
            telecomScope.launch {
                activateScope(scope)
            }
        } else if (currentRoomToken != null) {
            isPendingSetActive = true
            Log.d(TAG, "setCallActive queued: registration pending for room $currentRoomToken")
        }
    }

    private suspend fun activateScope(scope: CallControlScope) {
        // Exceptions propagate to telecomScope's handler (setCallActive) or addCall's catch (onScopeAssigned)
        val result = if (isIncomingCall) {
            val res = scope.answer(currentCallType)
            isIncomingCall = false
            res
        } else {
            scope.setActive()
        }
        if (result is CallControlResult.Error) {
            Log.e(TAG, "Failed to activate call in Core-Telecom: $result")
        } else {
            isCallAnsweredOrActive = true
            Log.d(TAG, "Call activated successfully in Core-Telecom")
        }
    }

    private fun prepareCallRegistration(roomToken: String, callTypeLabel: String): String? {
        if (roomToken.isBlank()) {
            Log.e(TAG, "Cannot add $callTypeLabel call: roomToken is blank")
            return null
        }
        if (currentRoomToken == roomToken || currentCallControlScope != null) {
            Log.d(TAG, "$callTypeLabel call ignored: registration or scope already active for room $currentRoomToken")
            return null
        }
        if (currentRoomToken != null) {
            Log.d(TAG, "$callTypeLabel call ignored: active call already exists for room $currentRoomToken")
            return null
        }
        val registrationId = UUID.randomUUID().toString()
        currentRegistrationId = registrationId
        currentRoomToken = roomToken
        isPendingSetActive = false
        return registrationId
    }

    private fun handleCallDisconnected(registrationId: String) {
        if (registrationId.isNotEmpty() && currentRegistrationId == registrationId) {
            currentCallControlScope = null
            currentRoomToken = null
            currentRegistrationId = null
            currentDisconnectHandler = null
            isPendingSetActive = false
            isIncomingCall = false
            isCallAnsweredOrActive = false
        }
    }

    private fun CallControlScope.onScopeAssigned(
        registrationId: String,
        roomToken: String,
        autoActivate: Boolean = false
    ) {
        if (currentRegistrationId == registrationId) {
            currentCallControlScope = this
            Log.d(TAG, "Call registered in CallControlScope for room $roomToken (id=$registrationId)")
            if (autoActivate || isPendingSetActive) {
                launch { activateScope(this@onScopeAssigned) }
                isPendingSetActive = false
                Log.d(TAG, "Call set active for room $roomToken (id=$registrationId)")
            }
        } else {
            Log.w(
                TAG,
                "CallControlScope obtained for stale registration (id=$registrationId, room=$roomToken), disconnecting"
            )
            launch { disconnect(DisconnectCause(DisconnectCause.REJECTED)) }
        }
    }

    @VisibleForTesting
    internal fun getCallType(isVideo: Boolean): Int =
        if (isVideo) CallAttributesCompat.CALL_TYPE_VIDEO_CALL else CallAttributesCompat.CALL_TYPE_AUDIO_CALL

    companion object {
        private val TAG = TelecomManager::class.java.simpleName
    }
}
