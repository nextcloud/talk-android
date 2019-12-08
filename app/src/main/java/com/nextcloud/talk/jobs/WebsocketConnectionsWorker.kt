/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.jobs

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.Worker
import androidx.work.WorkerParameters
import autodagger.AutoInjector
import com.bluelinelabs.logansquare.LoganSquare
import com.nextcloud.talk.application.NextcloudTalkApplication
import com.nextcloud.talk.models.ExternalSignalingServer
import com.nextcloud.talk.models.database.UserEntity
import com.nextcloud.talk.newarch.domain.repository.offline.UsersRepository
import com.nextcloud.talk.newarch.local.models.UserNgEntity
import com.nextcloud.talk.utils.database.user.UserUtils
import com.nextcloud.talk.webrtc.WebSocketConnectionHelper
import org.koin.core.KoinComponent
import org.koin.core.inject
import java.io.IOException
import javax.inject.Inject

@AutoInjector(NextcloudTalkApplication::class)
class WebsocketConnectionsWorker(
  context: Context,
  workerParams: WorkerParameters
) : Worker(context, workerParams), KoinComponent {

  val usersRepository: UsersRepository by inject()

  override fun doWork(): Result {
    NextcloudTalkApplication.sharedApplication!!
        .componentApplication
        .inject(this)

    val userEntityList = usersRepository.getUsers()
    var userEntity: UserNgEntity
    for (i in userEntityList.indices) {
      userEntity = userEntityList[i]
      if (userEntity.externalSignaling != null) {
          if (!userEntity.externalSignaling!!.externalSignalingServer.isNullOrEmpty() &&
              !userEntity.externalSignaling!!.externalSignalingTicket.isNullOrEmpty()) {
            WebSocketConnectionHelper.getExternalSignalingInstanceForServer(
                userEntity.externalSignaling!!.externalSignalingServer!!,
                userEntity, userEntity.externalSignaling!!.externalSignalingTicket,
                false
            )
          }

      }
    }

    return Result.success()
  }

  companion object {
    private val TAG = "WebsocketConnectionsWorker"
  }
}
