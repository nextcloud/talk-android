/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.app.Activity
import android.content.Context
import android.content.Intent

/**
 * The internal entry point of an exported activity: a non-exported activity-alias of it, named "Internal" plus the name
 * of the activity, in its package. Only the app itself can start the activity through it, so the activity can trust
 * the extras of intents that come through it, unlike those of intents that other apps send to the activity itself.
 */
object InternalEntryPoint {

    /** The class name of the internal entry point of [activity], as declared in the manifest. */
    fun nameOf(activity: Class<out Activity>): String =
        activity.name.substringBeforeLast('.') + ".Internal" + activity.simpleName

    /** Creates an intent that starts [activity] through its internal entry point. */
    fun createIntent(context: Context, activity: Class<out Activity>): Intent =
        Intent().setClassName(context, nameOf(activity))

    /** Whether [intent] was started through the internal entry point of [activity], so by the app itself. */
    fun isStartedThrough(intent: Intent, activity: Class<out Activity>): Boolean =
        intent.component?.className == nameOf(activity)

    /**
     * Passes [intent], which another app sent to an exported activity that doesn't handle it, on to [target], with
     * [flags] and the read grant of its uri.
     */
    fun createForwardIntent(context: Context, intent: Intent, target: Class<out Activity>, flags: Int = 0): Intent =
        Intent(intent)
            .setClass(context, target)
            .setFlags(flags or (intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION))
}
