/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Sowjanya Kota <sowjanya.kch@gmail.com>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.api

import com.nextcloud.talk.models.json.autocomplete.AutocompleteOverall
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.participants.AddParticipantOverall
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Url

interface NcApiCoroutines {
    @GET
    @JvmSuppressWildcards
    suspend fun getContactsWithSearchParam(
        @Header("Authorization") authorization: String?,
        @Url url: String?,
        @Query("shareTypes[]") listOfShareTypes: List<String>?,
        @QueryMap options: Map<String, Any>?
    ): AutocompleteOverall

    /*
        QueryMap items are as follows:
            - "roomType" : ""
            - "invite" : ""

        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room
     */
    @POST
    suspend fun createRoom(
        @Header("Authorization") authorization: String?,
        @Url url: String?,
        @QueryMap options: Map<String, String>?
    ): RoomOverall

    /*
        QueryMap items are as follows:
            - "roomName" : "newName"

        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room/roomToken
     */
    @FormUrlEncoded
    @PUT
    suspend fun renameRoom(
        @Header("Authorization") authorization: String?,
        @Url url: String,
        @Field("roomName") roomName: String?
    ): GenericOverall

    @FormUrlEncoded
    @PUT
    suspend fun openConversation(
        @Header("Authorization") authorization: String?,
        @Url url: String,
        @Field("scope") scope: Int
    ): GenericOverall

    @FormUrlEncoded
    @PUT
    suspend fun setConversationDescription(
        @Header("Authorization") authorization: String?,
        @Url url: String,
        @Field("description") description: String?
    ): GenericOverall

    @POST
    suspend fun addParticipant(
        @Header("Authorization") authorization: String?,
        @Url url: String?,
        @QueryMap options: Map<String, String>?
    ): AddParticipantOverall

    @POST
    suspend fun makeRoomPublic(@Header("Authorization") authorization: String, @Url url: String): GenericOverall

    @DELETE
    suspend fun makeRoomPrivate(@Header("Authorization") authorization: String, @Url url: String): GenericOverall

    @FormUrlEncoded
    @PUT
    suspend fun setPassword(
        @Header("Authorization") authorization: String?,
        @Url url: String?,
        @Field("password") password: String?
    ): GenericOverall

    @Multipart
    @POST
    suspend fun uploadConversationAvatar(
        @Header("Authorization") authorization: String,
        @Url url: String,
        @Part attachment: MultipartBody.Part
    ): RoomOverall

    @DELETE
    suspend fun deleteConversationAvatar(@Header("Authorization") authorization: String, @Url url: String): RoomOverall

    @POST
    suspend fun archiveConversation(@Header("Authorization") authorization: String, @Url url: String): GenericOverall

    @DELETE
    suspend fun unarchiveConversation(@Header("Authorization") authorization: String, @Url url: String): GenericOverall

    @POST
    suspend fun setReadStatusPrivacy(
        @Header("Authorization") authorization: String,
        @Url url: String,
        @Body body: RequestBody
    ): GenericOverall

    @POST
    suspend fun setTypingStatusPrivacy(
        @Header("Authorization") authorization: String,
        @Url url: String,
        @Body body: RequestBody
    ): GenericOverall

    @FormUrlEncoded
    @PUT
    suspend fun setPassword2(
        @Header("Authorization") authorization: String,
        @Url url: String,
        @Field("password") password: String
    ): GenericOverall

    @DELETE
    suspend fun clearChatHistory(
        @Header("Authorization") authorization: String,
        @Url url: String
    ): GenericOverall
}
