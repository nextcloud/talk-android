/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Julius Linus <juliuslinus1@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.data.io

/**
 * Abstract class used by manager classes in the data layer. Allows every Manager to handle the lifecycle events
 * observed by the view model.
 */
abstract class LifecycleAwareManager {
    /**
     * See [onPause](https://developer.android.com/guide/components/activities/activity-lifecycle#onpause)
     * for more details.
     */
    open fun handleOnPause() {}

    /**
     * See [onResume](https://developer.android.com/guide/components/activities/activity-lifecycle#onresume)
     * for more details.
     */
    open fun handleOnResume() {}

    /**
     * See [onStop](https://developer.android.com/guide/components/activities/activity-lifecycle#onstop)
     * for more details.
     */
    open fun handleOnStop() {}
}
