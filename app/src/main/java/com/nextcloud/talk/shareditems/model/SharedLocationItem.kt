/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.shareditems.model

import android.net.Uri

data class SharedLocationItem(
    override val id: String,
    override val name: String,
    override val actorId: String,
    override val actorName: String,
    override val dateTime: String,
    val geoUri: Uri
) : SharedItem
