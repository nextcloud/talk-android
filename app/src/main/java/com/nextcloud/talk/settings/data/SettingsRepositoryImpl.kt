/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.settings.data

import com.nextcloud.talk.api.NcApiCoroutines
import com.nextcloud.talk.data.user.model.User
import com.nextcloud.talk.models.json.userprofile.UserProfileDataDto
import com.nextcloud.talk.utils.ApiUtils
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

class SettingsRepositoryImpl @Inject constructor(private val ncApiCoroutines: NcApiCoroutines) : SettingsRepository {

    override suspend fun getUserProfile(user: User): UserProfileDataDto? =
        ncApiCoroutines.getUserProfile(
            user.getCredentials(),
            ApiUtils.getUrlForUserProfile(user.baseUrl!!)
        ).ocs?.data

    override suspend fun setPhoneNumber(user: User, phoneNumber: String): Boolean {
        val response = ncApiCoroutines.setUserData(
            user.getCredentials(),
            ApiUtils.getUrlForUserData(user.baseUrl!!, user.userId!!),
            KEY_PHONE,
            phoneNumber
        )
        return response.ocs?.meta?.statusCode == HTTP_OK
    }

    override suspend fun setReadStatusShared(user: User, shared: Boolean) {
        ncApiCoroutines.setReadStatusPrivacy(
            user.getCredentials(),
            ApiUtils.getUrlForUserSettings(user.baseUrl!!),
            privacySettingBody(KEY_READ_STATUS_PRIVACY, shared)
        )
    }

    override suspend fun setTypingStatusShared(user: User, shared: Boolean) {
        ncApiCoroutines.setTypingStatusPrivacy(
            user.getCredentials(),
            ApiUtils.getUrlForUserSettings(user.baseUrl!!),
            privacySettingBody(KEY_TYPING_PRIVACY, shared)
        )
    }

    /** The server expects 0 for public and 1 for private. */
    private fun privacySettingBody(key: String, shared: Boolean): RequestBody =
        JSONObject()
            .put("key", key)
            .put("value", if (shared) PRIVACY_PUBLIC else PRIVACY_PRIVATE)
            .toString()
            .toRequestBody(JSON_MEDIA_TYPE)

    companion object {
        private const val KEY_PHONE = "phone"
        private const val KEY_READ_STATUS_PRIVACY = "read_status_privacy"
        private const val KEY_TYPING_PRIVACY = "typing_privacy"
        private const val PRIVACY_PUBLIC = 0
        private const val PRIVACY_PRIVATE = 1
        private const val HTTP_OK = 200
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
