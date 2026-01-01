/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.raisehand

import io.reactivex.Observable

interface RequestAssistanceRepository {

    fun requestAssistance(credentials: String, url: String, roomToken: String): Observable<RequestAssistanceModel>

    fun withdrawRequestAssistance(
        credentials: String,
        url: String,
        roomToken: String
    ): Observable<WithdrawRequestAssistanceModel>
}
