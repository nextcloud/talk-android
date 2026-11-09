/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.chat

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.nextcloud.talk.activities.CallBaseActivity

/**
 * Tells when a voice recording of [chatActivity] has to end although the chat was not left: Talk went to the
 * background, where Android silences the microphone, or a Talk screen started that uses the microphone itself (a
 * call or another chat). Android does not silence a second capture from the same app, so these screens are checked on
 * every version; captures by other apps are reported by MediaRecorderManager from Android 10 on. Other Talk screens
 * on top of the chat, like the media viewer, keep the recording running.
 */
class VoiceRecordingInterrupter(private val chatActivity: Activity, private val onInterrupt: () -> Unit) :
    DefaultLifecycleObserver,
    Application.ActivityLifecycleCallbacks {

    fun register() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        chatActivity.application.registerActivityLifecycleCallbacks(this)
    }

    fun unregister() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(this)
        chatActivity.application.unregisterActivityLifecycleCallbacks(this)
    }

    /** The whole app went to the background. */
    override fun onStop(owner: LifecycleOwner) = onInterrupt()

    override fun onActivityStarted(activity: Activity) {
        if (activity !== chatActivity && usesMicrophone(activity)) {
            onInterrupt()
        }
    }

    private fun usesMicrophone(activity: Activity) = activity is CallBaseActivity || activity is ChatActivity

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit

    override fun onActivityResumed(activity: Activity) = Unit

    override fun onActivityPaused(activity: Activity) = Unit

    override fun onActivityStopped(activity: Activity) = Unit

    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

    override fun onActivityDestroyed(activity: Activity) = Unit
}
