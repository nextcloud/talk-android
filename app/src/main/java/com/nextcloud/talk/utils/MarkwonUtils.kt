/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.content.Context
import android.content.Intent
import android.text.method.ScrollingMovementMethod
import android.view.View
import androidx.core.net.toUri
import com.nextcloud.talk.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListDrawable
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.movement.MovementMethodPlugin

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
                    builder.linkResolver(object : LinkResolverDef() {
                        override fun resolve(view: View, link: String) {
                            var linkToOpen = link
                            if (!(linkToOpen.contains("http://") || linkToOpen.contains("https://"))) {
                                linkToOpen = "https://$link"
                            }
                            val browserIntent = Intent(
                                Intent.ACTION_VIEW,
                                linkToOpen.toUri()
                            )
                            context.startActivity(browserIntent)
                        }
                    })
                }
            })
            .usePlugin(TaskListPlugin.create(drawable))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(MovementMethodPlugin.create(ScrollingMovementMethod.getInstance()))
            .build()
    }
}
