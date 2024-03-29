/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.models.json.status

enum class StatusType(val string: String) {
    ONLINE("online"),
    OFFLINE("offline"),
    DND("dnd"),
    AWAY("away"),
    INVISIBLE("invisible")
}
