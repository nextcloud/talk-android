/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.View
import androidx.core.net.toUri
import com.nextcloud.talk.R
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolverDef
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListDrawable
import io.noties.markwon.ext.tasklist.TaskListPlugin

object MarkdownUtils {
    private const val TAG = "MarkdownUtils"

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
                            Log.i(TAG, "Link - $view / $link")
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
            .usePlugin(TablePlugin.create { _ -> })
            .build()
    }
}
