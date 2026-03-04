/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.status

import android.content.Context
import com.nextcloud.talk.R

enum class StatusType(val string: String) {
    ONLINE("online"),
    OFFLINE("offline"),
    DND("dnd"),
    AWAY("away"),
    BUSY("busy"),
    INVISIBLE("invisible");

    companion object {
        fun getDescription(value: String?, context: Context): String =
            when (value) {
                DND.string -> context.getString(R.string.dnd)
                BUSY.string -> context.getString(R.string.busy)
                AWAY.string -> context.getString(R.string.away)
                else -> ""
            }
    }
}
