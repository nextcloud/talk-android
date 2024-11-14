/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Tim Krüger <t@timkrueger.me
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.adapters

import com.nextcloud.talk.models.json.status.predefined.PredefinedStatus

interface PredefinedStatusClickListener {
    fun onClick(predefinedStatus: PredefinedStatus)
    fun revertStatus()
}
