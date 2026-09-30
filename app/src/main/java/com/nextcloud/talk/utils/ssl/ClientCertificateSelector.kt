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
 * The TLS handshake only knows the server, not the account of the request. So the certificate is chosen by the
 * server: the one of the accounts on it. Only if accounts on the same server use different certificates, the
 * default account decides, as the connection may then be reused by all of them.
 */
object ClientCertificateSelector {

    /**
     * Returns the alias of the client certificate for a connection to [host], or null if no account on it uses one.
     * Without a [host], the certificate of [defaultUser] is used.
     */
    @JvmStatic
    fun selectAlias(host: String?, users: List<User>, defaultUser: User?): String? {
        val usersOnHost = users.filter { host != null && hostOf(it).equals(host, ignoreCase = true) }
        val aliases = usersOnHost.mapNotNull { aliasOf(it) }.toSet()
        return when {
            host == null -> aliasOf(defaultUser)
            aliases.size <= 1 -> aliases.firstOrNull()
            else -> defaultUser?.takeIf { default -> usersOnHost.any { it.id == default.id } }?.let { aliasOf(it) }
        }
    }

    private fun aliasOf(user: User?): String? = user?.clientCertificate?.takeIf { it.isNotEmpty() }

    private fun hostOf(user: User): String? =
        try {
            user.baseUrl?.let { URI(it).host }
        } catch (e: URISyntaxException) {
            null
        }
}
