/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2019 Mario Danic <mario@lovelyhq.com>
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

package com.nextcloud.talk.newarch.data.source.remote

import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall
import com.nextcloud.talk.models.json.conversations.RoomOverall
import com.nextcloud.talk.models.json.conversations.RoomsOverall
import com.nextcloud.talk.models.json.generic.GenericOverall
import com.nextcloud.talk.models.json.push.PushRegistrationOverall
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall
import retrofit2.http.*

interface ApiService {

    @GET
    suspend fun getCapabilities(@Url url: String): CapabilitiesOverall

    @GET
    suspend fun getSignalingSettings(@Header("Authorization") authorization: String,
                                     @Url url: String): SignalingSettingsOverall

    @GET
    suspend fun getUserProfile(@Header("Authorization") authorization: String,
                               @Url url: String): UserProfileOverall

    /*
        QueryMap items are as follows:
            - "format" : "json"
            - "pushTokenHash" : ""
            - "devicePublicKey" : ""
            - "proxyServer" : ""

        Server URL is: baseUrl + ocsApiVersion + "/apps/notifications/api/v2/push
     */
    @POST
    fun registerForPushWithServer(
            @Header("Authorization") authorization: String,
            @Url url: String,
            @QueryMap options: Map<String, String>): PushRegistrationOverall

    @DELETE
    fun unregisterForPushWithServer(@Header("Authorization") authorization: String,
                                    @Url url: String): GenericOverall

    @FormUrlEncoded
    @POST
    fun registerForPushWithProxy(@Url url: String,
                                 @FieldMap fields: Map<String, String>): Any

    /*
        QueryMap items are as follows:
          - "deviceIdentifier": "{{deviceIdentifier}}",
          - "deviceIdentifierSignature": "{{signature}}",
          - "userPublicKey": "{{userPublicKey}}"
    */
    @DELETE
    fun unregisterForPushWithProxy(@Url url: String?,
                                   @QueryMap fields: Map<String, String>): Any

    @GET
    suspend fun getConversations(
            @Header(
                    "Authorization"
            ) authorization: String, @Url url: String
    ): RoomsOverall

    @POST
    suspend fun addConversationToFavorites(
            @Header("Authorization") authorization: String,
            @Url url: String
    ): GenericOverall

    @DELETE
    suspend fun removeConversationFromFavorites(
            @Header("Authorization") authorization: String,
            @Url url: String
    ): GenericOverall

    @DELETE
    suspend fun leaveConversation(
            @Header("Authorization") authorization: String,
            @Url url: String
    ): GenericOverall

    @DELETE
    suspend fun deleteConversation(
            @Header("Authorization") authorization: String,
            @Url url: String
    ): GenericOverall

    @GET
    suspend fun getConversation(@Header("Authorization") authorization: String, @Url url: String): RoomOverall

    @FormUrlEncoded
    @POST
    suspend fun joinConversation(@Header("Authorization") authorization: String,
                                 @Url url: String, @Field("password") password: String?): RoomOverall

    @DELETE
    suspend fun exitConversation(@Header("Authorization") authorization: String,
                                 @Url url: String): GenericOverall

}
