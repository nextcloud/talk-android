/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

/**
 * Interface used by manager classes in the data layer. Enforces that every Manager handles the lifecycle events
 * observed by the view model.
 */
interface LifecycleAwareManager {
    /**
     * See [onPause](https://developer.android.com/guide/components/activities/activity-lifecycle#onpause)
     * for more details.
     */
    fun handleOnPause()

    /**
     * See [onResume](https://developer.android.com/guide/components/activities/activity-lifecycle#onresume)
     * for more details.
     */
    fun handleOnResume()

    /**
     * See [onStop](https://developer.android.com/guide/components/activities/activity-lifecycle#onstop)
     * for more details.
     */
    fun handleOnStop()
}
