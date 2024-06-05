/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Your Name <your@email.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.api

import com.nextcloud.talk.models.json.conversations.RoomOverall
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface NcAPI {
    @GET
    suspend fun getContactsWithSearchParam(
        @Header("Authorization") authorization: String,
        @Url url: String,
        @Query("shareTypes[]") listOfShareTypes: List<String>?,
        @QueryMap options: Map<String, Any>
    ): ResponseBody

    /*
        QueryMap items are as follows:
            - "roomType" : ""
            - "invite" : ""

        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room
     */
    @POST
    suspend fun createRoom(
        @Header("Authorization") authorization: String,
        @Url url: String,
        @QueryMap options: Map<String, String>
    ): RoomOverall
}
