/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.model

interface SharedItem {
    val id: String
    val name: String
    val actorId: String
    val actorName: String
    val dateTime: String
}
