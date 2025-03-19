/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.model

import android.util.Log
import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.database.dao.UserCirclesOrGroupsDao
import com.nextcloud.talk.utils.ApiUtils
import com.nextcloud.talk.utils.database.user.CurrentUserProviderNew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UserGroupsCirclesRepository @Inject constructor(
    private val userCirclesOrGroupsDao: UserCirclesOrGroupsDao,
    private val ncApiCoroutines: NcApiCoroutines,
    private val currentUserProvider: CurrentUserProviderNew
) {
    val user = currentUserProvider.currentUser.blockingGet()
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val credentials: String = ApiUtils.getCredentials(user.username, user.token)!!

            coroutineScope {
                launch {
                   userCirclesOrGroupsDao.deleteAllUserGroups()
                    val response = ncApiCoroutines.getUserGroups(
                        credentials,
                        ApiUtils.getUrlForUserGroups(
                            user.baseUrl!!,
                            user.userId!!
                        )
                    )
                    val groups = response.ocs?.data?.groups?: emptyList()
                    Log.d("UserDataRepo","$groups")
                    userCirclesOrGroupsDao.insertUserGroups(
                        groups.map{
                            UserGroupsEntity(it)
                        }
                    )
                }

                launch {
                    userCirclesOrGroupsDao.deleteAllUserCircles()
                    val response = ncApiCoroutines.getUserCircles(
                        credentials,
                        ApiUtils.getUrlForUserCircles(user.baseUrl!!)
                    )
                    val circles = response.ocs?.data?.map { it.displayName!! }?: emptyList()
                    Log.d("UserDataRepo","$circles")
                    userCirclesOrGroupsDao.insertUserCircles(
                        circles.map{
                            UserCirclesEntity(it)
                        }
                    )
                }
            }

            return@withContext true
        } catch (e: Exception) {
            Log.e("UserDataRepo", "Error initializing user data", e)
            return@withContext false
        }

    }

   fun getUserGroups(): List<UserGroupsEntity> {
        return userCirclesOrGroupsDao.getUserGroups()
    }

   fun getUserCircles(): List<UserCirclesEntity>{
        return userCirclesOrGroupsDao.getUserCircles()
    }
}
