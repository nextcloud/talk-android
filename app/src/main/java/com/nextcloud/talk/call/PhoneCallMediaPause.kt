/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.call

/**
 * Decides which local media to turn off while a cellular phone call is active, and which to turn on again when it
 * ends. Only media that was turned off for the phone call is restored, and only when the user did not change it
 * during the phone call.
 */
class PhoneCallMediaPause {

    data class Media(val microphone: Boolean, val camera: Boolean)

    var isPhoneCallActive = false
        private set

    private var pausedMicrophone = false
    private var pausedCamera = false

    /**
     * Returns the media to turn off, based on the [current] media.
     */
    fun onPhoneCallStarted(current: Media): Media {
        if (isPhoneCallActive) {
            return NONE
        }
        isPhoneCallActive = true
        pausedMicrophone = current.microphone
        pausedCamera = current.camera
        return Media(pausedMicrophone, pausedCamera)
    }

    /**
     * Returns the media to turn on again, based on the [current] media.
     */
    fun onPhoneCallEnded(current: Media): Media {
        if (!isPhoneCallActive) {
            return NONE
        }
        val restore = Media(
            microphone = pausedMicrophone && !current.microphone,
            camera = pausedCamera && !current.camera
        )
        reset()
        return restore
    }

    /**
     * Returns whether the microphone must stay off instead of being turned on automatically, as a phone call is
     * active. It is then turned on when the phone call ends.
     */
    fun holdBackMicrophone(): Boolean {
        if (isPhoneCallActive) {
            pausedMicrophone = true
        }
        return isPhoneCallActive
    }

    /**
     * Returns whether the camera must stay off instead of being turned on automatically, as a phone call is active.
     * It is then turned on when the phone call ends.
     */
    fun holdBackCamera(): Boolean {
        if (isPhoneCallActive) {
            pausedCamera = true
        }
        return isPhoneCallActive
    }

    fun onMicrophoneChangedByUser() {
        pausedMicrophone = false
    }

    fun onCameraChangedByUser() {
        pausedCamera = false
    }

    fun reset() {
        isPhoneCallActive = false
        pausedMicrophone = false
        pausedCamera = false
    }

    companion object {
        val NONE = Media(microphone = false, camera = false)
    }
}
