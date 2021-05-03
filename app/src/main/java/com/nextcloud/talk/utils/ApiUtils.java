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
package com.nextcloud.talk.utils;

import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.nextcloud.talk.BuildConfig;
import com.nextcloud.talk.R;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.models.database.UserEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.DimenRes;
import androidx.annotation.Nullable;
import okhttp3.Credentials;

public class ApiUtils {
    private static final String TAG = "ApiUtils";
    private static String ocsApiVersion = "/ocs/v2.php";
    private static String spreedApiVersion = "/apps/spreed/api/v1";
    private static String spreedApiBase = ocsApiVersion + "/apps/spreed/api/v";

    private static String userAgent = "Mozilla/5.0 (Android) Nextcloud-Talk v";

    public static String getUserAgent() {
        return userAgent + BuildConfig.VERSION_NAME;
    }

    /**
     * @deprecated Please specify the api version you want to use via
     * {@link ApiUtils#getUrlForRoomWebinaryLobby(int, String, String)} instead.
     */
    @Deprecated
    public static String getUrlForLobbyForConversation(String baseUrl, String token) {
        return getUrlForRoomWebinaryLobby(1, baseUrl, token);
    }

    @Deprecated
    public static String getUrlForRemovingParticipantFromConversation(String baseUrl, String roomToken, boolean isGuest) {
        String url = getUrlForParticipants(1, baseUrl, roomToken);

        if (isGuest) {
            url += "/guests";
        }

        return url;
    }

    public static RetrofitBucket getRetrofitBucketForContactsSearch(String baseUrl, @Nullable String searchQuery) {
        RetrofitBucket retrofitBucket = new RetrofitBucket();
        retrofitBucket.setUrl(baseUrl + ocsApiVersion + "/apps/files_sharing/api/v1/sharees");

        Map<String, String> queryMap = new HashMap<>();

        if (searchQuery == null) {
            searchQuery = "";
        }
        queryMap.put("format", "json");
        queryMap.put("search", searchQuery);
        queryMap.put("itemType", "call");

        retrofitBucket.setQueryMap(queryMap);

        return retrofitBucket;
    }

    public static String getUrlForFilePreviewWithRemotePath(String baseUrl, String remotePath, int px) {
        return baseUrl + "/index.php/core/preview.png?file="
                + Uri.encode(remotePath, "UTF-8")
                + "&x=" + px + "&y=" + px + "&a=1&mode=cover&forceIcon=1";
    }

    public static String getUrlForFilePreviewWithFileId(String baseUrl, String fileId, int px) {
        return baseUrl + "/index.php/core/preview?fileId="
                + fileId + "&x=" + px + "&y=" + px + "&a=1&mode=cover&forceIcon=1";
    }

    public static String getSharingUrl(String baseUrl) {
        return baseUrl + ocsApiVersion + "/apps/files_sharing/api/v1/shares";
    }

    public static RetrofitBucket getRetrofitBucketForContactsSearchFor14(String baseUrl, @Nullable String searchQuery) {
        RetrofitBucket retrofitBucket = getRetrofitBucketForContactsSearch(baseUrl, searchQuery);
        retrofitBucket.setUrl(baseUrl + ocsApiVersion + "/core/autocomplete/get");

        retrofitBucket.getQueryMap().put("itemId", "new");

        return retrofitBucket;
    }

    /**
     * @deprecated Please specify the api version you want to use via
     * {@link ApiUtils#getUrlForParticipantsActive(int, String, String)} instead.
     */
    @Deprecated
    public static String getUrlForSettingMyselfAsActiveParticipant(String baseUrl, String token) {
        return getUrlForParticipantsActive(1, baseUrl, token);
    }


    /**
     * @deprecated Please specify the api version you want to use via
     * {@link ApiUtils#getUrlForParticipants(int, String, String)} instead.
     */
    @Deprecated
    public static String getUrlForParticipants(String baseUrl, String token) {
        return getUrlForParticipants(1, baseUrl, token);
    }

    public static String getUrlForCapabilities(String baseUrl) {
        return baseUrl + ocsApiVersion + "/cloud/capabilities";
    }

    /**
     * @deprecated Please specify the api version you want to use via
     * {@link ApiUtils#getUrlForRooms(int, String)} instead.
     */
    @Deprecated
    public static String getUrlForGetRooms(String baseUrl) {
        return getUrlForRooms(1, baseUrl);
    }

    /**
     * @deprecated Please specify the api version you want to use via
     * {@link ApiUtils#getUrlForRoom(int, String, String)} instead.
     */
    @Deprecated
    public static String getRoom(String baseUrl, String token) {
        return getUrlForRoom(1, baseUrl, token);
    }

    public static Integer getApiVersion(UserEntity capabilities, String apiName, int[] versions) {
        boolean checkedConversationV4 = !apiName.equals("conversation");

        for (int version : versions) {
            checkedConversationV4 |= version == 4;

            if (capabilities.hasSpreedFeatureCapability(apiName + "-v" + version)) {
                if (!checkedConversationV4) {
                    Exception e = new Exception("Api call did not try conversation-v4 api");
                    Log.e(TAG, e.getMessage(), e);
                }

                return version;
            }
        }
        return null;
    }

    protected static String getUrlForApi(int version, String baseUrl) {
        return baseUrl + spreedApiBase + version;
    }

    public static String getUrlForRooms(int version, String baseUrl) {
        return getUrlForApi(version, baseUrl) + "/room";
    }

    public static String getUrlForRoom(int version, String baseUrl, String token) {
        return getUrlForRooms(version, baseUrl) + "/" + token;
    }

    public static String getUrlForParticipants(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/participants";
    }

    public static String getUrlForParticipantsActive(int version, String baseUrl, String token) {
        return getUrlForParticipants(version, baseUrl, token) + "/active";
    }

    public static String getUrlForParticipantsSelf(int version, String baseUrl, String token) {
        return getUrlForParticipants(version, baseUrl, token) + "/self";
    }

    public static String getUrlForRoomFavorite(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/favorite";
    }

    public static String getUrlForRoomModerators(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/moderators";
    }

    public static String getUrlForRoomNotificationLevel(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/notify";
    }

    public static String getUrlForRoomPublic(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/public";
    }

    public static String getUrlForRoomPassword(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/password";
    }

    public static String getUrlForRoomReadOnlyState(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/read-only";
    }

    public static String getUrlForRoomWebinaryLobby(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/webinary/lobby";
    }

    @Deprecated
    public static RetrofitBucket getRetrofitBucketForCreateRoom(String baseUrl, String roomType,
                                                                @Nullable String invite,
                                                                @Nullable String conversationName) {
        RetrofitBucket retrofitBucket = new RetrofitBucket();
        retrofitBucket.setUrl(baseUrl + ocsApiVersion + spreedApiVersion + "/room");
        Map<String, String> queryMap = new HashMap<>();

        queryMap.put("roomType", roomType);
        if (invite != null) {
            queryMap.put("invite", invite);
        }

        if (conversationName != null) {
            queryMap.put("roomName", conversationName);
        }

        retrofitBucket.setQueryMap(queryMap);

        return retrofitBucket;
    }

    @Deprecated
    public static RetrofitBucket getRetrofitBucketForAddParticipant(String baseUrl, String token, String user) {
        RetrofitBucket retrofitBucket = new RetrofitBucket();
        retrofitBucket.setUrl(baseUrl + ocsApiVersion + spreedApiVersion + "/room/" + token + "/participants");

        Map<String, String> queryMap = new HashMap<>();

        queryMap.put("newParticipant", user);

        retrofitBucket.setQueryMap(queryMap);

        return retrofitBucket;

    }

    public static RetrofitBucket getRetrofitBucketForAddGroupParticipant(String baseUrl, String token, String group) {
        RetrofitBucket retrofitBucket = getRetrofitBucketForAddParticipant(baseUrl, token, group);
        retrofitBucket.getQueryMap().put("source", "groups");
        return retrofitBucket;
    }

    public static RetrofitBucket getRetrofitBucketForAddMailParticipant(String baseUrl, String token, String mail) {
        RetrofitBucket retrofitBucket = getRetrofitBucketForAddParticipant(baseUrl, token, mail);
        retrofitBucket.getQueryMap().put("source", "emails");
        return retrofitBucket;
    }

    @Deprecated
    public static String getUrlForCall(String baseUrl, String token) {
        // FIXME user APIv4
        return baseUrl + ocsApiVersion + spreedApiVersion + "/call/" + token;

    }

    @Deprecated
    public static String getUrlForCallPing(String baseUrl, String token) {
        return getUrlForCall(baseUrl, token) + "/ping";
    }

    public static String getUrlForChat(String baseUrl, String token) {
        return baseUrl + ocsApiVersion + spreedApiVersion + "/chat/" + token;
    }

    @Deprecated
    public static String getUrlForExternalServerAuthBackend(String baseUrl) {
        return getUrlForSignaling(baseUrl, null) + "/backend";
    }

    public static String getUrlForMentionSuggestions(String baseUrl, String token) {
        return getUrlForChat(baseUrl, token) + "/mentions";
    }

    @Deprecated
    public static String getUrlForSignaling(String baseUrl, @Nullable String token) {
        // FIXME use APIv2 ?
        String signalingUrl = baseUrl + ocsApiVersion + spreedApiVersion + "/signaling";
        if (token == null) {
            return signalingUrl;
        } else {
            return signalingUrl + "/" + token;
        }
    }

    /**
     * @deprecated Please specify the api version you want to use via
     * {@link ApiUtils#getUrlForRoomModerators(int, String, String)} instead.
     */
    @Deprecated
    public static String getUrlForModerators(String baseUrl, String roomToken) {
        return getUrlForRoomModerators(1, baseUrl, roomToken);
    }

    @Deprecated
    public static String getUrlForSignalingSettings(String baseUrl) {
        return getUrlForSignaling(baseUrl, null) + "/settings";
    }


    public static String getUrlForUserProfile(String baseUrl) {
        return baseUrl + ocsApiVersion + "/cloud/user";
    }

    public static String getUrlForUserData(String baseUrl, String userId) {
        return baseUrl + ocsApiVersion + "/cloud/users/" + userId;
    }

    public static String getUrlForUserSettings(String baseUrl) {
        return baseUrl + ocsApiVersion + spreedApiVersion + "/settings/user";
    }

    public static String getUrlPostfixForStatus() {
        return "/status.php";
    }

    public static String getUrlForAvatarWithNameAndPixels(String baseUrl, String name, int avatarSize) {
        return baseUrl + "/index.php/avatar/" + Uri.encode(name) + "/" + avatarSize;
    }

    public static String getUrlForAvatarWithName(String baseUrl, String name, @DimenRes int avatarSize) {
        avatarSize = Math.round(NextcloudTalkApplication
                .Companion.getSharedApplication().getResources().getDimension(avatarSize));

        return baseUrl + "/index.php/avatar/" + Uri.encode(name) + "/" + avatarSize;
    }

    public static String getUrlForAvatarWithNameForGuests(String baseUrl, String name,
                                                          @DimenRes int avatarSize) {
        avatarSize = Math.round(NextcloudTalkApplication
                .Companion.getSharedApplication().getResources().getDimension(avatarSize));

        return baseUrl + "/index.php/avatar/guest/" + Uri.encode(name) + "/" + avatarSize;
    }

    public static String getCredentials(String username, String token) {
        if (TextUtils.isEmpty(username) && TextUtils.isEmpty(token)) {
            return null;
        }
        return Credentials.basic(username, token);
    }

    public static String getUrlNextcloudPush(String baseUrl) {
        return baseUrl + ocsApiVersion + "/apps/notifications/api/v2/push";
    }

    public static String getUrlPushProxy() {
        return NextcloudTalkApplication.Companion.getSharedApplication().
                getApplicationContext().getResources().getString(R.string.nc_push_server_url) + "/devices";
    }

    public static String getUrlForNotificationWithId(String baseUrl, String notificationId) {
        return baseUrl + ocsApiVersion + "/apps/notifications/api/v2/notifications/" + notificationId;
    }

    public static String getUrlForSearchByNumber(String baseUrl) {
        return baseUrl + ocsApiVersion + "/cloud/users/search/by-phone";
    }

    public static String getUrlForFileUpload(String baseUrl, String user, String attachmentFolder, String filename) {
        return baseUrl + "/remote.php/dav/files/" + user + attachmentFolder + "/" + filename;
    }

    public static String getUrlForFileDownload(String baseUrl, String user, String remotePath) {
        return baseUrl + "/remote.php/dav/files/" + user + "/" + remotePath;
    }

    public static String getUrlForMessageDeletion(String baseUrl, String token, String messageId) {
        return getUrlForChat(baseUrl, token) + "/" + messageId;
    }

    public static String getUrlForTempAvatar(String baseUrl) {
        return baseUrl + ocsApiVersion + "/apps/spreed/temp-user-avatar";
    }

    public static String getUrlForUserFields(String baseUrl) {
        return baseUrl + ocsApiVersion + "/cloud/user/fields";
    }
}
