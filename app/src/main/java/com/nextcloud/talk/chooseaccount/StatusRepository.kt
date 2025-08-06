/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chooseaccount

import com.nextcloud.talk.models.json.status.StatusOverall

interface StatusRepository {
    suspend fun setStatus(): StatusOverall
}
