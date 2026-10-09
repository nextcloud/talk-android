/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.nextcloud.talk.chat.audio

/**
 * Decides whether the credentials of an account may be attached to an audio request. Only requests for the WebDAV
 * files of that account on its own server qualify, so credentials are never sent to any other host, whatever URL
 * ends up in the player.
 */
object ChatAudioCredentials {
    private const val USER_FILES_PATH = "/remote.php/dav/files/"

    /**
     * The prefix of every file URL of the account, built the same way as
     * [com.nextcloud.talk.utils.ApiUtils.getUrlForFileDownload].
     */
    fun userFilesPrefix(baseUrl: String, userId: String): String = "$baseUrl$USER_FILES_PATH$userId/"

    fun belongsToUser(url: String, baseUrl: String?, userId: String?): Boolean =
        !baseUrl.isNullOrEmpty() && !userId.isNullOrEmpty() && url.startsWith(userFilesPrefix(baseUrl, userId))
}
