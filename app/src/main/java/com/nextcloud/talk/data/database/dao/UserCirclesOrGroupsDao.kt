/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2025 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nextcloud.talk.data.database.model.UserCirclesEntity
import com.nextcloud.talk.data.database.model.UserGroupsEntity

@Dao
interface UserCirclesOrGroupsDao {

    @Query("SELECT groups FROM user_groups")
    fun getUserGroups(): List<UserGroupsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserGroups(groups: List<UserGroupsEntity>)

    @Query("SELECT displayName FROM user_circles")
    fun getUserCircles(): List<UserCirclesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserCircles(circles: List<UserCirclesEntity>)

    @Query("DELETE FROM user_groups")
    suspend fun deleteAllUserGroups()

    @Query("DELETE FROM user_circles")
    suspend fun deleteAllUserCircles()
}
