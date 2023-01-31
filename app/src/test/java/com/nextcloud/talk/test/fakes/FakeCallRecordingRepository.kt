/*
 * Nextcloud Talk application
 *
 * @author Álvaro Brey
 * Copyright (C) 2022 Álvaro Brey
 * Copyright (C) 2022 Nextcloud GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.test.fakes

import com.nextcloud.talk.models.domain.StartCallRecordingModel
import com.nextcloud.talk.models.domain.StopCallRecordingModel
import com.nextcloud.talk.repositories.callrecording.CallRecordingRepository
import io.reactivex.Observable

class FakeCallRecordingRepository : CallRecordingRepository {

    override fun startRecording(
        roomToken: String
    ): Observable<StartCallRecordingModel> {
        return Observable.just(StartCallRecordingModel(true))
    }

    override fun stopRecording(
        roomToken: String
    ): Observable<StopCallRecordingModel> {
        return Observable.just(StopCallRecordingModel(true))
    }
}
