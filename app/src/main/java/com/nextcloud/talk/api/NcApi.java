/*
 *
 *   Nextcloud Talk application
 *
 *   @author Mario Danic
 *   Copyright (C) 2017 Mario Danic (mario@lovelyhq.com)
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.nextcloud.talk.api;

import androidx.annotation.Nullable;
import com.nextcloud.talk.models.json.call.CallOverall;
import com.nextcloud.talk.models.json.capabilities.CapabilitiesOverall;
import com.nextcloud.talk.models.json.chat.ChatOverall;
import com.nextcloud.talk.models.json.generic.GenericOverall;
import com.nextcloud.talk.models.json.generic.Status;
import com.nextcloud.talk.models.json.mention.MentionOverall;
import com.nextcloud.talk.models.json.notifications.NotificationOverall;
import com.nextcloud.talk.models.json.participants.AddParticipantOverall;
import com.nextcloud.talk.models.json.participants.ParticipantsOverall;
import com.nextcloud.talk.models.json.push.PushRegistrationOverall;
import com.nextcloud.talk.models.json.rooms.RoomOverall;
import com.nextcloud.talk.models.json.rooms.RoomsOverall;
import com.nextcloud.talk.models.json.signaling.SignalingOverall;
import com.nextcloud.talk.models.json.signaling.settings.SignalingSettingsOverall;
import com.nextcloud.talk.models.json.userprofile.UserProfileOverall;
import io.reactivex.Observable;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.http.*;

import java.util.List;
import java.util.Map;

public interface NcApi {

    /*
        QueryMap items are as follows:
            - "format" : "json"
            - "search" : ""
            - "perPage" : "200"
            - "itemType" : "call"

        Server URL is: baseUrl + ocsApiVersion + /apps/files_sharing/api/v1/sharees

        or if we're on 14 and up:

        baseUrl + ocsApiVersion + "/core/autocomplete/get");

     */
    @GET
    Observable<ResponseBody> getContactsWithSearchParam(@Header("Authorization") String authorization, @Url String url,
                                                        @Nullable @Query("shareTypes[]") List<String> listOfShareTypes, @QueryMap Map<String, Object> options);


    /*
        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room
     */
    @GET
    Observable<RoomsOverall> getRooms(@Header("Authorization") String authorization, @Url String url);

    /*
        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room/roomToken
    */
    @GET
    Observable<RoomOverall> getRoom(@Header("Authorization") String authorization, @Url String url);

    /*
        QueryMap items are as follows:
            - "roomType" : ""
            - "invite" : ""

        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room
     */

    @POST
    Observable<RoomOverall> createRoom(@Header("Authorization") String authorization, @Url String url,
                                       @QueryMap Map<String, String> options);

    /*
        QueryMap items are as follows:
            - "roomName" : "newName"

        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room/roomToken
     */

    @FormUrlEncoded
    @PUT
    Observable<GenericOverall> renameRoom(@Header("Authorization") String authorization, @Url String url,
                                          @Field("roomName") String roomName);


    /*
        QueryMap items are as follows:
            - "newParticipant" : "user"

        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room/roomToken/participants
    */
    @POST
    Observable<AddParticipantOverall> addParticipant(@Header("Authorization") String authorization, @Url String url,
                                                     @QueryMap Map<String, String> options);


    /*
        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room/roomToken/participants/self
     */

    @DELETE
    Observable<GenericOverall> removeSelfFromRoom(@Header("Authorization") String authorization, @Url String url);

    /*
        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room/roomToken/public
    */
    @POST
    Observable<GenericOverall> makeRoomPublic(@Header("Authorization") String authorization, @Url String url);

    /*
        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /room/roomToken/public
    */
    @DELETE
    Observable<GenericOverall> makeRoomPrivate(@Header("Authorization") String authorization, @Url String url);

    @DELETE
    Observable<GenericOverall> deleteRoom(@Header("Authorization") String authorization, @Url String url);

    /*
        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /call/callToken
    */
    @GET
    Observable<ParticipantsOverall> getPeersForCall(@Header("Authorization") String authorization, @Url String url);

    @FormUrlEncoded
    @POST
    Observable<CallOverall> joinRoom(@Nullable @Header("Authorization") String authorization, @Url String url,
                                     @Nullable @Field("password") String password);

    @DELETE
    Observable<GenericOverall> leaveRoom(@Nullable @Header("Authorization") String authorization, @Url String url);

    /*
        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /call/callToken
    */

    @POST
    Observable<GenericOverall> joinCall(@Nullable @Header("Authorization") String authorization, @Url String url);

    /*
    Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /call/callToken
    */
    @DELETE
    Observable<GenericOverall> leaveCall(@Nullable @Header("Authorization") String authorization, @Url String url);

    /*
        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /call/callToken/ping
    */
    @POST
    Observable<GenericOverall> pingCall(@Nullable @Header("Authorization") String authorization, @Url String url);

    @GET
    Observable<SignalingSettingsOverall> getSignalingSettings(@Nullable @Header("Authorization") String authorization,
                                                              @Url String url);

    /*
        QueryMap items are as follows:
            - "messages" : "message"

        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /signaling
    */
    @FormUrlEncoded
    @POST
    Observable<SignalingOverall> sendSignalingMessages(@Nullable @Header("Authorization") String authorization, @Url String url,
                                                       @Field("messages") String messages);

    /*
        Server URL is: baseUrl + ocsApiVersion + spreedApiVersion + /signaling
    */
    @GET
    Observable<SignalingOverall> pullSignalingMessages(@Nullable @Header("Authorization") String authorization, @Url
            String
            url);

     /*
        QueryMap items are as follows:
            - "format" : "json"

        Server URL is: baseUrl + ocsApiVersion + "/cloud/user"
    */

    @GET
    Observable<UserProfileOverall> getUserProfile(@Header("Authorization") String authorization, @Url String url);

    /*
        Server URL is: baseUrl + /status.php
     */
    @GET
    Observable<Status> getServerStatus(@Url String url);


    /*
        QueryMap items are as follows:
            - "format" : "json"
            - "pushTokenHash" : ""
            - "devicePublicKey" : ""
            - "proxyServer" : ""

        Server URL is: baseUrl + ocsApiVersion + "/apps/notifications/api/v2/push
     */

    @POST
    Observable<PushRegistrationOverall> registerDeviceForNotificationsWithNextcloud(@Header("Authorization")
                                                                                            String authorization,
                                                                                    @Url String url,
                                                                                    @QueryMap Map<String,
                                                                                            String> options);

    @DELETE
    Observable<GenericOverall> unregisterDeviceForNotificationsWithNextcloud(@Header("Authorization")
                                                                                     String authorization,
                                                                             @Url String url);

    @FormUrlEncoded
    @POST
    Observable<Void> registerDeviceForNotificationsWithProxy(@Header("Authorization") String authorization,
                                                             @Url String url,
                                                             @FieldMap Map<String, String> fields);


    /*
        QueryMap items are as follows:
          - "deviceIdentifier": "{{deviceIdentifier}}",
          - "deviceIdentifierSignature": "{{signature}}",
          - "userPublicKey": "{{userPublicKey}}"
    */
    @DELETE
    Observable<Void> unregisterDeviceForNotificationsWithProxy(@Header("Authorization") String authorization,
                                                               @Url String url,
                                                               @QueryMap Map<String, String> fields);

    @FormUrlEncoded
    @PUT
    Observable<GenericOverall> setPassword(@Header("Authorization") String authorization, @Url String url,
                                           @Field("password") String password);

    @GET
    Observable<CapabilitiesOverall> getCapabilities(@Header("Authorization") String authorization, @Url String url);

    /*
       QueryMap items are as follows:
         - "lookIntoFuture": int (0 or 1),
         - "limit" : int, range 100-200,
         - "timeout": used with look into future, 30 default, 60 at most
         - "lastKnownMessageId", int, use one from X-Chat-Last-Given
   */
    @GET
    Observable<Response<ChatOverall>> pullChatMessages(@Header("Authorization") String authorization, @Url String url,
                                                       @QueryMap Map<String, Integer> fields);

    /*
        Fieldmap items are as follows:
          - "message": ,
          - "actorDisplayName"
    */

    @FormUrlEncoded
    @POST
    Observable<GenericOverall> sendChatMessage(@Header("Authorization") String authorization, @Url String url,
                                               @Field("message") CharSequence message,
                                               @Field("actorDisplayName") String actorDisplayName);

    @GET
    Observable<MentionOverall> getMentionAutocompleteSuggestions(@Header("Authorization") String authorization,
                                                                 @Url String url, @Query("search") String query,
                                                                 @Nullable @Query("limit") Integer limit);

    // Url is: /api/{apiVersion}/room/{token}/pin
    @POST
    Observable<GenericOverall> addConversationToFavorites(@Header("Authorization") String authorization,
                                                          @Url String url);

    // Url is: /api/{apiVersion}/room/{token}/favorites
    @DELETE
    Observable<GenericOverall> removeConversationFromFavorites(@Header("Authorization") String authorization,
                                                               @Url String url);

    @GET
    Observable<NotificationOverall> getNotification(@Header("Authorization") String authorization,
                                                    @Url String url);

    @FormUrlEncoded
    @POST
    Observable<GenericOverall> setNotificationLevel(@Header("Authorization") String authorization, @Url String url, @Field("level") int level);

    @FormUrlEncoded
    @PUT
    Observable<GenericOverall> setReadOnlyState(@Header("Authorization") String authorization, @Url String url, @Field("state") int state);


    @FormUrlEncoded
    @POST
    Observable<Void> createRemoteShare(@Nullable @Header("Authorization") String authorization, @Url String url,
                                       @Field("path") String remotePath,
                                       @Field("shareWith") String roomToken,
                                       @Field("shareType") String shareType);
}
