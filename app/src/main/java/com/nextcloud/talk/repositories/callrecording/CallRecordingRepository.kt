/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2022 Marcel Hibbe <dev@mhibbe.de>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.repositories.callrecording

import com.nextcloud.talk.models.domain.StartCallRecordingModel
import com.nextcloud.talk.models.domain.StopCallRecordingModel
import io.reactivex.Observable

interface CallRecordingRepository {

    fun startRecording(credentials: String?, url: String, roomToken: String): Observable<StartCallRecordingModel>

    fun stopRecording(credentials: String?, url: String, roomToken: String): Observable<StopCallRecordingModel>
}
