/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * @author Tim Krüger
 * Copyright (C) 2021 Tim Krüger <t@timkrueger.me>
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
import com.nextcloud.talk.data.user.model.User;
import com.nextcloud.talk.models.RetrofitBucket;
import com.nextcloud.talk.models.database.CapabilitiesUtil;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.utils.database.user.CapabilitiesUtilNew;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;
import okhttp3.Credentials;

public class ApiUtils {
    public static final int APIv1 = 1;
    public static final int APIv2 = 2;
    public static final int APIv3 = 3;
    public static final int APIv4 = 4;
    public static final int AVATAR_SIZE_BIG = 512;
    public static final int AVATAR_SIZE_SMALL = 64;
    private static final String TAG = "ApiUtils";
    private static final String ocsApiVersion = "/ocs/v2.php";
    private static final String spreedApiVersion = "/apps/spreed/api/v1";
    private static final String spreedApiBase = ocsApiVersion + "/apps/spreed/api/v";

    private static final String userAgent = "Mozilla/5.0 (Android) Nextcloud-Talk v";

    public static String getUserAgent() {
        return userAgent + BuildConfig.VERSION_NAME;
    }

    /**
     * @deprecated This is only supported on API v1-3, in API v4+ please use
     * {@link ApiUtils#getUrlForAttendees(int, String, String)} instead.
     */
    @Deprecated
    public static String getUrlForRemovingParticipantFromConversation(String baseUrl, String roomToken, boolean isGuest) {
        String url = getUrlForParticipants(APIv1, baseUrl, roomToken);

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

    public static String getUrlForCapabilities(String baseUrl) {
        return baseUrl + ocsApiVersion + "/cloud/capabilities";
    }

    public static int getCallApiVersion(UserEntity capabilities, int[] versions) throws NoSupportedApiException {
        return getConversationApiVersion(capabilities, versions);
    }

    public static int getConversationApiVersion(User user, int[] versions) throws NoSupportedApiException {
        boolean hasApiV4 = false;
        for (int version : versions) {
            hasApiV4 |= version == APIv4;
        }

        if (!hasApiV4) {
            Exception e = new Exception("Api call did not try conversation-v4 api");
            Log.d(TAG, e.getMessage(), e);
        }

        for (int version : versions) {
            if (user.hasSpreedFeatureCapability("conversation-v" + version)) {
                return version;
            }

            // Fallback for old API versions
            if ((version == APIv1 || version == APIv2)) {
                if (user.hasSpreedFeatureCapability("conversation-v2")) {
                    return version;
                }
                if (version == APIv1  &&
                    user.hasSpreedFeatureCapability("mention-flag") &&
                    !user.hasSpreedFeatureCapability("conversation-v4")) {
                    return version;
                }
            }
        }
        throw new NoSupportedApiException();
    }

    @Deprecated
    public static int getConversationApiVersion(UserEntity user, int[] versions) throws NoSupportedApiException {
        return getConversationApiVersion(LegacyUserEntityMapper.toModel(user), versions);
    }

    public static int getSignalingApiVersion(UserEntity user, int[] versions) throws NoSupportedApiException {
        for (int version : versions) {
            if (CapabilitiesUtil.hasSpreedFeatureCapability(user, "signaling-v" + version)) {
                return version;
            }

            if (version == APIv2 &&
                    CapabilitiesUtil.hasSpreedFeatureCapability(user, "sip-support") &&
                    !CapabilitiesUtil.hasSpreedFeatureCapability(user, "signaling-v3")) {
                return version;
            }

            if (version == APIv1 &&
                    !CapabilitiesUtil.hasSpreedFeatureCapability(user, "signaling-v3")) {
                // Has no capability, we just assume it is always there when there is no v3 or later
                return version;
            }
        }
        throw new NoSupportedApiException();
    }

    public static int getChatApiVersion(UserEntity user, int[] versions) throws NoSupportedApiException {
        for (int version : versions) {
            if (version == APIv1 && CapabilitiesUtil.hasSpreedFeatureCapability(user, "chat-v2")) {
                // Do not question that chat-v2 capability shows the availability of api/v1/ endpoint *see no evil*
                return version;
            }
        }
        throw new NoSupportedApiException();
    }

    public static int getChatApiVersion(User user, int[] versions) throws NoSupportedApiException {
        for (int version : versions) {
            if (version == APIv1 && CapabilitiesUtilNew.hasSpreedFeatureCapability(user, "chat-v2")) {
                // Do not question that chat-v2 capability shows the availability of api/v1/ endpoint *see no evil*
                return version;
            }
        }
        throw new NoSupportedApiException();
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

    public static String getUrlForAttendees(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/attendees";
    }

    public static String getUrlForParticipants(int version, String baseUrl, String token) {
        if (token == null || token.isEmpty()){
            Log.e(TAG, "token was null or empty");
        }
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

    public static String getUrlForRoomNotificationCalls(int version, String baseUrl, String token) {
        return getUrlForRoom(version, baseUrl, token) + "/notify-calls";
    }

    public static String getUrlForCall(int version, String baseUrl, String token) {
        return getUrlForApi(version, baseUrl) + "/call/" + token;
    }
    public static String getUrlForChat(int version, String baseUrl, String token) {
        return getUrlForApi(version, baseUrl) + "/chat/" + token;
    }

    public static String getUrlForMentionSuggestions(int version, String baseUrl, String token) {
        return getUrlForChat(version, baseUrl, token) + "/mentions";
    }
    public static String getUrlForChatMessage(int version, String baseUrl, String token, String messageId) {
        return getUrlForChat(version, baseUrl, token) + "/" + messageId;
    }
    
    public static String getUrlForChatSharedItems(int version, String baseUrl, String token) {
        return getUrlForChat(version, baseUrl, token) + "/share";
    }

    public static String getUrlForChatSharedItemsOverview(int version, String baseUrl, String token) {
        return getUrlForChatSharedItems(version, baseUrl, token) + "/overview";
    }

    public static String getUrlForSignaling(int version, String baseUrl) {
        return getUrlForApi(version, baseUrl) + "/signaling";
    }

    public static String getUrlForSignalingBackend(int version, String baseUrl) {
        return getUrlForSignaling(version, baseUrl) + "/backend";
    }

    public static String getUrlForSignalingSettings(int version, String baseUrl) {
        return getUrlForSignaling(version, baseUrl) + "/settings";
    }

    public static String getUrlForSignaling(int version, String baseUrl, String token) {
        return getUrlForSignaling(version, baseUrl) + "/" + token;
    }

    public static String getUrlForOpenConversations(int version, String baseUrl) {
        return getUrlForApi(version, baseUrl) + "/listed-room";
    }

    public static RetrofitBucket getRetrofitBucketForCreateRoom(int version, String baseUrl, String roomType,
                                                                @Nullable String source,
                                                                @Nullable String invite,
                                                                @Nullable String conversationName) {
        RetrofitBucket retrofitBucket = new RetrofitBucket();
        retrofitBucket.setUrl(getUrlForRooms(version, baseUrl));
        Map<String, String> queryMap = new HashMap<>();

        queryMap.put("roomType", roomType);
        if (invite != null) {
            queryMap.put("invite", invite);
        }
        if (source != null) {
            queryMap.put("source", source);
        }

        if (conversationName != null) {
            queryMap.put("roomName", conversationName);
        }

        retrofitBucket.setQueryMap(queryMap);

        return retrofitBucket;
    }

    public static RetrofitBucket getRetrofitBucketForAddParticipant(int version, String baseUrl, String token, String user) {
        RetrofitBucket retrofitBucket = new RetrofitBucket();
        retrofitBucket.setUrl(getUrlForParticipants(version, baseUrl, token));

        Map<String, String> queryMap = new HashMap<>();

        queryMap.put("newParticipant", user);

        retrofitBucket.setQueryMap(queryMap);

        return retrofitBucket;

    }

    public static RetrofitBucket getRetrofitBucketForAddParticipantWithSource(
            int version,
            String baseUrl,
            String token,
            String source,
            String id
                                                                             ) {
        RetrofitBucket retrofitBucket = getRetrofitBucketForAddParticipant(version, baseUrl, token, id);
        retrofitBucket.getQueryMap().put("source", source);
        return retrofitBucket;
    }

    public static String getUrlForUserProfile(String baseUrl) {
        return baseUrl + ocsApiVersion + "/cloud/user";
    }

    public static String getUrlForUserData(String baseUrl, String userId) {
        return baseUrl + ocsApiVersion + "/cloud/users/" + userId;
    }

    public static String getUrlForUserSettings(String baseUrl) {
        // FIXME Introduce API version
        return baseUrl + ocsApiVersion + spreedApiVersion + "/settings/user";
    }

    public static String getUrlPostfixForStatus() {
        return "/status.php";
    }

    public static String getUrlForAvatar(String baseUrl, String name, boolean requestBigSize) {
        int avatarSize = requestBigSize ? AVATAR_SIZE_BIG : AVATAR_SIZE_SMALL;
        return baseUrl + "/index.php/avatar/" + Uri.encode(name) + "/" + avatarSize;
    }

    public static String getUrlForGuestAvatar(String baseUrl, String name, boolean requestBigSize) {
        int avatarSize = requestBigSize ? AVATAR_SIZE_BIG : AVATAR_SIZE_SMALL;
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

    public static String getUrlForTempAvatar(String baseUrl) {
        return baseUrl + ocsApiVersion + "/apps/spreed/temp-user-avatar";
    }

    public static String getUrlForUserFields(String baseUrl) {
        return baseUrl + ocsApiVersion + "/cloud/user/fields";
    }

    public static String getUrlToSendLocation(int version, String baseUrl, String roomToken) {
        return getUrlForChat(version, baseUrl, roomToken) + "/share";
    }

    public static String getUrlForHoverCard(String baseUrl, String userId) { return baseUrl + ocsApiVersion +
        "/hovercard/v1/" + userId; }

    public static String getUrlForSetChatReadMarker(int version, String baseUrl, String roomToken) {
        return getUrlForChat(version, baseUrl, roomToken) + "/read";
    }

    /*
     * OCS Status API
     */

    public static String getUrlForStatus(String baseUrl) {
        return baseUrl + ocsApiVersion + "/apps/user_status/api/v1/user_status";
    }

    public static String getUrlForSetStatusType(String baseUrl) {
        return getUrlForStatus(baseUrl) + "/status";
    }

    public static String getUrlForPredefinedStatuses(String baseUrl) {
        return baseUrl + ocsApiVersion + "/apps/user_status/api/v1/predefined_statuses";
    }

    public static String getUrlForStatusMessage(String baseUrl) {
        return getUrlForStatus(baseUrl) + "/message";
    }

    public static String getUrlForSetCustomStatus(String baseUrl) {
        return baseUrl + ocsApiVersion + "/apps/user_status/api/v1/user_status/message/custom";
    }

    public static String getUrlForSetPredefinedStatus(String baseUrl) {
        return baseUrl + ocsApiVersion + "/apps/user_status/api/v1/user_status/message/predefined";
    }

    public static String getUrlForUserStatuses(String baseUrl) {
        return baseUrl + ocsApiVersion + "/apps/user_status/api/v1/statuses";
    }

    public static String getUrlForMessageReaction(String baseUrl,
                                                  String roomToken,
                                                  String messageId) {
        return baseUrl + ocsApiVersion + spreedApiVersion + "/reaction/" + roomToken + "/" + messageId;
    }

    @NotNull
    public static String getUrlForUnifiedSearch(@NotNull String baseUrl, @NotNull String providerId) {
        return baseUrl + ocsApiVersion + "/search/providers/" + providerId + "/search";
    }
}
