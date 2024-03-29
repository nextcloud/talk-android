/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2023 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.raisehand

import io.reactivex.Observable

interface RequestAssistanceRepository {

    fun requestAssistance(roomToken: String): Observable<RequestAssistanceModel>

    fun withdrawRequestAssistance(roomToken: String): Observable<WithdrawRequestAssistanceModel>
}
