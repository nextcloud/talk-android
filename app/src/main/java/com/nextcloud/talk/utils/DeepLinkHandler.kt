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
 * Supported URI format (per https://github.com/nextcloud/spreed/issues/16354):
 * - nextcloudtalk://[userid@]server_host/[server_base/]call/token
 *
 * Examples:
 * - nextcloudtalk://cloud.example.com/call/abc123
 * - nextcloudtalk://user1@cloud.example.com/call/abc123
 * - nextcloudtalk://cloud.example.com/nextcloud/call/abc123
 * - nextcloudtalk://user1@cloud.example.com/index.php/call/abc123
 */
object DeepLinkHandler {

    private const val SCHEME_NEXTCLOUD_TALK = "nextcloudtalk"
    private const val PATH_CALL = "call"
    private const val PATH_INDEX_PHP = "index.php"

    // Token validation: alphanumeric characters, reasonable length
    private val TOKEN_PATTERN = Regex("^[a-zA-Z0-9]{4,32}$")

    /**
     * Result of parsing a deep link URI.
     *
     * @property roomToken The conversation/room token to open
     * @property serverUrl The server URL extracted from the deep link
     * @property username Optional username from the URI authority (user@host format)
     */
    data class DeepLinkResult(val roomToken: String, val serverUrl: String, val username: String? = null)

    /**
     * Parses a deep link URI and extracts conversation information.
     *
     * @param uri The URI to parse
     * @return DeepLinkResult if the URI is valid, null otherwise
     */
    fun parseDeepLink(uri: Uri): DeepLinkResult? {
        if (uri.scheme?.lowercase() != SCHEME_NEXTCLOUD_TALK) {
            return null
        }
        return parseNextcloudTalkUri(uri)
    }

    /**
     * Parses a nextcloudtalk:// URI.
     * Format: nextcloudtalk://[userid@]server_host/[server_base/]call/token
     */
    @Suppress("ReturnCount")
    private fun parseNextcloudTalkUri(uri: Uri): DeepLinkResult? {
        val authority = uri.authority ?: return null
        val path = uri.path ?: return null
        val (username, serverHost) = parseAuthority(authority)
        val token = extractTokenFromPath(path)

        return if (serverHost.isBlank() || token == null || !isValidToken(token)) {
            null
        } else {
            DeepLinkResult(roomToken = token, serverUrl = "https://$serverHost", username = username)
        }
    }

    /**
     * Parses the authority part to extract optional username and server host.
     * Format: [userid@]server_host
     *
     * @return Pair of (username or null, serverHost)
     */
    private fun parseAuthority(authority: String): Pair<String?, String> =
        if (authority.contains("@")) {
            val parts = authority.split("@", limit = 2)
            val username = parts[0].takeIf { it.isNotBlank() }
            val host = parts.getOrElse(1) { "" }
            Pair(username, host)
        } else {
            Pair(null, authority)
        }

    /**
     * Extracts the room token from the path.
     * Matches /call/{token} or /[anything]/call/{token} patterns.
     */
    private fun extractTokenFromPath(path: String): String? {
        // Match patterns like /call/token or /base/call/token or /index.php/call/token
        val tokenRegex = Regex("/$PATH_CALL/([^/]+)/?$")
        val match = tokenRegex.find(path) ?: return null
        return match.groupValues[1].takeIf { it.isNotBlank() }
    }

    /**
     * Validates that a token matches the expected format.
     * Tokens should be alphanumeric and between 4-32 characters.
     */
    private fun isValidToken(token: String): Boolean = TOKEN_PATTERN.matches(token)

    /**
     * Creates a custom scheme URI for a conversation.
     *
     * @param roomToken The conversation token
     * @param serverUrl The server base URL (e.g., "https://cloud.example.com")
     * @param username Optional username for multi-account support
     * @return URI in the format nextcloudtalk://[user@]host/call/token
     */
    fun createConversationUri(roomToken: String, serverUrl: String, username: String? = null): Uri {
        // Extract host from server URL
        val serverUri = Uri.parse(serverUrl)
        val host = serverUri.host ?: return Uri.EMPTY

        // Build authority with optional username
        val authority = if (username != null) {
            "$username@$host"
        } else {
            host
        }

        return Uri.Builder()
            .scheme(SCHEME_NEXTCLOUD_TALK)
            .authority(authority)
            .appendPath(PATH_CALL)
            .appendPath(roomToken)
            .build()
    }
}
