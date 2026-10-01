/*
 * Nextcloud Talk - Android Client
 *
 * SPDX-FileCopyrightText: 2026 Nextcloud GmbH and Nextcloud contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.nextcloud.talk.utils.ssl

import com.nextcloud.talk.data.user.model.User
import java.net.URI
import java.net.URISyntaxException

/**
 * Chooses the client certificate of the account a TLS connection is for.
 *
 * The TLS handshake only knows the server (host and port), not the account of the request. So the certificate is
 * chosen by the server: the one of the accounts on it. Only if accounts on the same server use different certificates,
 * the default account decides, as the connection may then be reused by all of them.
 */
object ClientCertificateSelector {

    /**
     * Returns the alias of the client certificate for a connection to [host] and [port], or null if no account on
     * that server uses one. Without a [host], the certificate of [defaultUser] is used. A negative [port] is unknown,
     * then the host alone identifies the server.
     */
    @JvmStatic
    fun selectAlias(host: String?, port: Int, users: List<User>, defaultUser: User?): String? {
        val usersOnServer = users.filter { host != null && isOnServer(it, host, port) }
        val aliases = usersOnServer.mapNotNull { aliasOf(it) }.toSet()
        return when {
            host == null -> aliasOf(defaultUser)
            aliases.size <= 1 -> aliases.firstOrNull()
            else -> defaultUser?.takeIf { default -> usersOnServer.any { it.id == default.id } }?.let { aliasOf(it) }
        }
    }

    private fun isOnServer(user: User, host: String, port: Int): Boolean {
        val uri = uriOf(user) ?: return false
        return uri.host.equals(host, ignoreCase = true) && (port < 0 || effectivePortOf(uri) == port)
    }

    private fun aliasOf(user: User?): String? = user?.clientCertificate?.takeIf { it.isNotEmpty() }

    private fun uriOf(user: User): URI? =
        try {
            user.baseUrl?.let { URI(it) }
        } catch (e: URISyntaxException) {
            null
        }

    // The base URL usually has no explicit port, then the one of its scheme applies.
    private fun effectivePortOf(uri: URI): Int =
        when {
            uri.port != -1 -> uri.port
            uri.scheme.equals("http", ignoreCase = true) -> HTTP_PORT
            else -> HTTPS_PORT
        }

    private const val HTTP_PORT = 80
    private const val HTTPS_PORT = 443
}
