/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.content.Context
import android.util.Log
import android.view.View
import com.nextcloud.talk.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListDrawable
import io.noties.markwon.ext.tasklist.TaskListPlugin

object MarkwonUtils {
    private const val TAG = "MarkwonUtils"

    fun build(context: Context, textColor: Int): Markwon {
        val drawable = TaskListDrawable(textColor, textColor, context.getColor(R.color.bg_default))
        return Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.isLinkUnderlined(true).headingBreakHeight(0)
                }

                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { view: View?, link: String? ->
                        Log.i(TAG, "Link action not implemented $view / $link")
                    }
                }
            })
            .usePlugin(TaskListPlugin.create(drawable))
            .usePlugin(StrikethroughPlugin.create())
            .build()
    }
}
