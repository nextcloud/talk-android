/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2024 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils

import android.net.Uri

/**
 * Handles parsing of deep links for opening conversations.
 *
 * Supported URI formats:
 * - nctalk://conversation/{token}
 * - nctalk://conversation/{token}?user={internalUserId}
 * - https://{server}/call/{token}
 * - https://{server}/index.php/call/{token}
 */
object DeepLinkHandler {

    private const val SCHEME_NCTALK = "nctalk"
    private const val HOST_CONVERSATION = "conversation"
    private const val QUERY_PARAM_USER = "user"
    private const val PATH_CALL = "call"
    private const val PATH_INDEX_PHP = "index.php"

    /**
     * Result of parsing a deep link URI.
     *
     * @property roomToken The conversation/room token to open
     * @property internalUserId Optional internal user ID for multi-account support
     * @property serverUrl Optional server URL extracted from web links
     */
    data class DeepLinkResult(
        val roomToken: String,
        val internalUserId: Long? = null,
        val serverUrl: String? = null
    )

    /**
     * Parses a deep link URI and extracts conversation information.
     *
     * @param uri The URI to parse
     * @return DeepLinkResult if the URI is valid, null otherwise
     */
    fun parseDeepLink(uri: Uri): DeepLinkResult? {
        return when (uri.scheme?.lowercase()) {
            SCHEME_NCTALK -> parseNcTalkUri(uri)
            "http", "https" -> parseWebUri(uri)
            else -> null
        }
    }

    /**
     * Parses a custom scheme URI (nctalk://conversation/{token}).
     */
    private fun parseNcTalkUri(uri: Uri): DeepLinkResult? {
        if (uri.host?.lowercase() != HOST_CONVERSATION) {
            return null
        }

        val pathSegments = uri.pathSegments
        if (pathSegments.isEmpty()) {
            return null
        }

        val token = pathSegments[0]
        if (token.isBlank()) {
            return null
        }

        val userId = uri.getQueryParameter(QUERY_PARAM_USER)?.toLongOrNull()

        return DeepLinkResult(
            roomToken = token,
            internalUserId = userId
        )
    }

    /**
     * Parses a web URL (https://{server}/call/{token} or https://{server}/index.php/call/{token}).
     */
    private fun parseWebUri(uri: Uri): DeepLinkResult? {
        val path = uri.path ?: return null
        val host = uri.host ?: return null

        // Match /call/{token} or /index.php/call/{token}
        val tokenRegex = Regex("^(?:/$PATH_INDEX_PHP)?/$PATH_CALL/([^/]+)/?$")
        val match = tokenRegex.find(path) ?: return null
        val token = match.groupValues[1]

        if (token.isBlank()) {
            return null
        }

        val serverUrl = "${uri.scheme}://$host"

        return DeepLinkResult(
            roomToken = token,
            serverUrl = serverUrl
        )
    }

    /**
     * Creates a custom scheme URI for a conversation.
     *
     * @param roomToken The conversation token
     * @param internalUserId Optional user ID for multi-account support
     * @return URI in the format nctalk://conversation/{token}?user={userId}
     */
    fun createConversationUri(roomToken: String, internalUserId: Long? = null): Uri {
        val builder = Uri.Builder()
            .scheme(SCHEME_NCTALK)
            .authority(HOST_CONVERSATION)
            .appendPath(roomToken)

        internalUserId?.let {
            builder.appendQueryParameter(QUERY_PARAM_USER, it.toString())
        }

        return builder.build()
    }
}
